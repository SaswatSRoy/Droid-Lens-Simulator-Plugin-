package com.droidlens.analysis

import android.content.Context
import android.util.Log
import com.droidlens.db.DroidLensDatabase
import com.droidlens.db.entities.BaselineSnapshot
import com.droidlens.instrumentation.ComposeRecompositionTracker
import com.droidlens.instrumentation.FrameInstrumentation
import com.droidlens.instrumentation.MemoryWatcher
import com.droidlens.instrumentation.StartupTracer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages baseline performance metrics for comparison during sessions.
 * Persists baselines to Room database.
 */
class BaselineManager(
    context: Context,
    private val frameInstrumentation: FrameInstrumentation,
    private val memoryWatcher: MemoryWatcher,
    private val recompositionTracker: ComposeRecompositionTracker,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "DroidLens-Baseline"
    }

    private val db = DroidLensDatabase.getInstance(context)
    private val baselineDao = db.baselineDao()

    private val _latestBaseline = MutableStateFlow<BaselineSnapshot?>(null)
    val latestBaseline = _latestBaseline.asStateFlow()

    /**
     * Captures current metrics and stores them as a baseline.
     */
    fun captureBaseline(sessionName: String) {
        scope.launch {
            val frameMetrics = frameInstrumentation.metrics.value
            val heapMetrics = memoryWatcher.heapMetrics.value
            val recompositions = recompositionTracker.getRecompositionSnapshot()

            val snapshot = BaselineSnapshot(
                sessionName = sessionName,
                capturedAtMs = System.currentTimeMillis(),
                avgFrameTimeMs = frameMetrics.avgFrameTimeMs,
                p95FrameTimeMs = frameMetrics.p95FrameTimeMs,
                jankFrameCount = frameMetrics.jankFrameCount,
                composableRecompositions = Json.encodeToString(recompositions),
                usedMemoryMb = heapMetrics.usedMemoryMb,
                maxMemoryMb = heapMetrics.maxMemoryMb,
                startupTimeMs = StartupTracer.getMetricsSnapshot().totalStartupMs
            )

            baselineDao.insertBaseline(snapshot)
            _latestBaseline.value = snapshot
            Log.d(TAG, "Baseline captured for session: $sessionName")
        }
    }

    /**
     * Loads the latest baseline for a session.
     */
    fun loadLatestBaseline(sessionName: String) {
        scope.launch {
            val baseline = baselineDao.getLatestBaseline(sessionName)
            _latestBaseline.value = baseline
            Log.d(TAG, "Baseline loaded for session: $sessionName (Found: ${baseline != null})")
        }
    }

    /**
     * Observes the latest baseline for a session.
     */
    fun observeLatestBaseline(sessionName: String): Flow<BaselineSnapshot?> {
        return baselineDao.observeLatestBaseline(sessionName)
    }

    /**
     * Clears all baselines for a session.
     */
    fun clearBaseline(sessionName: String) {
        scope.launch {
            baselineDao.deleteBaselinesForSession(sessionName)
            _latestBaseline.value = null
            Log.d(TAG, "Baselines cleared for session: $sessionName")
        }
    }
}
