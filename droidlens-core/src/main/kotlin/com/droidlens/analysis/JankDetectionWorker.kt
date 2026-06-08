package com.droidlens.analysis

import android.util.Log
import com.droidlens.model.JankEvent
import com.droidlens.model.Regression
import com.droidlens.model.RegressionType
import com.droidlens.model.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Monitors jank events and categorizes them into regressions.
 */
class JankDetectionWorker(
    private val jankEvents: SharedFlow<JankEvent>,
    private val regressionDetector: RegressionDetector,
    private val sessionName: String,
    private val jankThresholdMs: Long
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var lastJankTimeMs = 0L

    fun start() {
        scope.launch {
            jankEvents.collect { event ->
                processJankEvent(event)
            }
        }
    }

    private fun processJankEvent(event: JankEvent) {
        // Debounce identical events within 500ms
        val now = System.currentTimeMillis()
        if (now - lastJankTimeMs < 500) return
        lastJankTimeMs = now

        val severity = when {
            event.consecutiveJankCount >= 3 -> Severity.CRITICAL
            else -> Severity.HIGH
        }

        val regression = Regression(
            type = RegressionType.JankFrame(
                frameTimeMs = event.frameTimeMs.toLong(),
                threshold = jankThresholdMs
            ),
            severity = severity,
            detectedAtMs = now,
            sessionName = sessionName,
            rawData = "{\"frameTimeMs\": ${event.frameTimeMs}, \"consecutiveCount\": ${event.consecutiveJankCount}}"
        )

        Log.d("DroidLens-Frame", "Jank detected: ${event.frameTimeMs}ms (Consecutive: ${event.consecutiveJankCount}) - Severity: $severity")
        regressionDetector.reportRegression(regression)
    }
}
