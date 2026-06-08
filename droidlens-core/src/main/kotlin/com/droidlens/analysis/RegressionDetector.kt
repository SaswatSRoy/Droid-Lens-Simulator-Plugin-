package com.droidlens.analysis

import android.content.Context
import android.util.Log
import com.droidlens.db.DroidLensDatabase
import com.droidlens.db.entities.DetectedIssue
import com.droidlens.instrumentation.ComposeRecompositionTracker
import com.droidlens.instrumentation.FrameInstrumentation
import com.droidlens.instrumentation.MemoryWatcher
import com.droidlens.instrumentation.StartupTracer
import com.droidlens.model.JankEvent
import com.droidlens.model.RecompositionSpike
import com.droidlens.model.Regression
import com.droidlens.model.RegressionType
import com.droidlens.model.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Central engine for detecting performance regressions by comparing live metrics with baselines.
 */
class RegressionDetector(
    context: Context,
    private val frameInstrumentation: FrameInstrumentation,
    private val recompositionTracker: ComposeRecompositionTracker,
    private val memoryWatcher: MemoryWatcher,
    private val baselineManager: BaselineManager,
    private val frameJankThresholdMs: Long = 16L,
    private val memoryGrowthThresholdMb: Float = 50f,
    private val startupRegressionThresholdMs: Long = 200L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job())
) {
    companion object {
        private const val TAG = "DroidLens-Regression"
    }

    private val db = DroidLensDatabase.getInstance(context)
    private val issueDao = db.detectedIssueDao()

    private val _regressions = MutableSharedFlow<Regression>(extraBufferCapacity = 100)
    val regressions: SharedFlow<Regression> = _regressions.asSharedFlow()
    
    private val _currentSessionRegressions = mutableListOf<Regression>()
    val currentSessionRegressions: List<Regression> get() = _currentSessionRegressions.toList()

    private val lastEmissionTimes = ConcurrentHashMap<String, Long>()

    /**
     * Reports a new regression manually.
     */
    fun reportRegression(regression: Regression) {
        emitRegression(regression)
    }
    
    fun clearSession() {
        _currentSessionRegressions.clear()
        lastEmissionTimes.clear()
    }

    fun start() {
        // Collect from all instrumentation sources
        scope.launch {
            frameInstrumentation.jankEvents.collect { handleJankEvent(it) }
        }
        scope.launch {
            recompositionTracker.spikes.collect { handleRecompositionSpike(it) }
        }
        scope.launch {
            memoryWatcher.memoryRegressions.collect { handleMemoryRegression(it) }
        }

        // Check startup regression once metrics are available
        scope.launch {
            StartupTracer.metrics.collect { metrics ->
                if (metrics != null && metrics.isCaptured) {
                    checkStartupRegression(metrics.totalStartupMs)
                }
            }
        }
    }

    private fun handleJankEvent(event: JankEvent) {
        val severity = when {
            event.frameTimeMs > frameJankThresholdMs * 3 -> Severity.CRITICAL
            event.frameTimeMs > frameJankThresholdMs * 2 -> Severity.HIGH
            else -> Severity.MEDIUM
        }
        
        val regression = Regression(
            type = RegressionType.JankFrame(event.frameTimeMs.toLong(), frameJankThresholdMs),
            severity = severity,
            detectedAtMs = System.currentTimeMillis(),
            sessionName = baselineManager.latestBaseline.value?.sessionName ?: "default",
            rawData = "{\"frameTimeMs\": ${event.frameTimeMs}, \"threshold\": $frameJankThresholdMs}"
        )
        emitRegression(regression)
    }

    private fun handleRecompositionSpike(spike: RecompositionSpike) {
        val severity = when {
            spike.currentCount > spike.baselineCount * 5 -> Severity.CRITICAL
            spike.currentCount > spike.baselineCount * 3 -> Severity.HIGH
            else -> Severity.MEDIUM
        }

        val regression = Regression(
            type = RegressionType.RecompositionSpike(spike.composableName, spike.currentCount, spike.baselineCount),
            severity = severity,
            detectedAtMs = System.currentTimeMillis(),
            sessionName = baselineManager.latestBaseline.value?.sessionName ?: "default",
            rawData = "{\"count\": ${spike.currentCount}, \"baseline\": ${spike.baselineCount}}"
        )
        emitRegression(regression)
    }

    private fun handleMemoryRegression(regression: Regression) {
        // Memory regressions from MemoryWatcher already have baseline comparison, 
        // but we'll re-calculate severity based on Phase 6 logic.
        val growthBytes = (regression.type as? RegressionType.MemoryLeak)?.retainedSizeBytes ?: 0L
        val growthMb = growthBytes / 1024f / 1024f
        
        val severity = when {
            growthMb > memoryGrowthThresholdMb * 2 -> Severity.CRITICAL
            growthMb > memoryGrowthThresholdMb * 1.5 -> Severity.HIGH
            else -> Severity.MEDIUM
        }

        emitRegression(regression.copy(severity = severity))
    }

    private fun emitRegression(regression: Regression) {
        val typeKey = regression.type::class.java.simpleName
        val now = System.currentTimeMillis()
        val lastTime = lastEmissionTimes[typeKey] ?: 0L
        
        // Deduplicate: max once per 2000ms per type
        if (now - lastTime < 2000) return
        
        lastEmissionTimes[typeKey] = now
        _currentSessionRegressions.add(regression)
        _regressions.tryEmit(regression)
        
        scope.launch {
            issueDao.insertIssue(
                DetectedIssue(
                    sessionName = regression.sessionName,
                    regressionType = typeKey,
                    severity = regression.severity.name,
                    detectedAtMs = regression.detectedAtMs,
                    rawDataJson = regression.rawData
                )
            )
            Log.d("DroidLens-Regression", "Regression detected & persisted: ${regression.type} (${regression.severity})")
        }
    }

    /**
     * Checks if the startup time exceeds the baseline + threshold.
     */
    private fun checkStartupRegression(totalStartupMs: Long) {
        val baseline = baselineManager.latestBaseline.value
        val baselineStartupMs = baseline?.startupTimeMs ?: 0L

        // If no baseline, check against absolute threshold
        val threshold = if (baselineStartupMs > 0) {
            baselineStartupMs + startupRegressionThresholdMs
        } else {
            startupRegressionThresholdMs * 3 // Default absolute threshold: 600ms
        }

        if (totalStartupMs > threshold) {
            val severity = when {
                totalStartupMs > threshold * 2 -> Severity.CRITICAL
                totalStartupMs > threshold * 1.5 -> Severity.HIGH
                else -> Severity.MEDIUM
            }

            val regression = Regression(
                type = RegressionType.SlowStartup(
                    phaseMs = totalStartupMs,
                    threshold = threshold
                ),
                severity = severity,
                detectedAtMs = System.currentTimeMillis(),
                sessionName = baselineManager.latestBaseline.value?.sessionName ?: "default",
                rawData = "{\"totalStartupMs\": $totalStartupMs, \"baselineMs\": $baselineStartupMs, \"thresholdMs\": $threshold}"
            )
            emitRegression(regression)
            Log.w(TAG, "STARTUP REGRESSION: ${totalStartupMs}ms vs threshold ${threshold}ms")
        }
    }
}
