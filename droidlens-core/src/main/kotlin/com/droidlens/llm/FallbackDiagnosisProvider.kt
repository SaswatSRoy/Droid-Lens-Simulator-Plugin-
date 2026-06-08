package com.droidlens.llm

import com.droidlens.model.DiagnosisResult
import com.droidlens.model.Regression
import com.droidlens.model.RegressionType

/**
 * Provides rule-based fallback diagnoses when the LLM is unavailable.
 *
 * Used when:
 * - [DroidLensConfig.llmEnabled] is false
 * - Model file doesn't exist on device
 * - LLM initialization fails
 * - Device doesn't have enough RAM
 *
 * These are hardcoded but useful generic diagnoses that cover the most common
 * causes for each regression type.
 */
object FallbackDiagnosisProvider {

    /** Confidence value for fallback diagnoses (lower than LLM to differentiate). */
    private const val FALLBACK_CONFIDENCE = 0.5f

    /**
     * Returns a rule-based diagnosis for the given regression.
     */
    fun diagnose(regression: Regression): DiagnosisResult.Success {
        return when (regression.type) {
            is RegressionType.JankFrame -> diagnoseJankFrame(regression.type)
            is RegressionType.RecompositionSpike -> diagnoseRecompositionSpike(regression.type)
            is RegressionType.MemoryLeak -> diagnoseMemoryLeak(regression.type)
            is RegressionType.SlowStartup -> diagnoseSlowStartup(regression.type)
        }
    }

    private fun diagnoseJankFrame(jank: RegressionType.JankFrame): DiagnosisResult.Success {
        val explanation = buildString {
            append("Frame exceeded render budget (${jank.frameTimeMs}ms vs ${jank.threshold}ms threshold). ")
            append("This typically indicates blocking operations on the main thread.")
        }
        val suggestion = buildString {
            append("Check for blocking operations on the main thread (StrictMode can help identify these). ")
            append("Consider using LaunchedEffect for async work. ")
            append("Move disk I/O, network calls, and heavy computation to Dispatchers.IO.")
        }
        return DiagnosisResult.Success(
            regressionType = jank,
            explanation = explanation,
            suggestion = suggestion,
            confidence = FALLBACK_CONFIDENCE
        )
    }

    private fun diagnoseRecompositionSpike(
        spike: RegressionType.RecompositionSpike
    ): DiagnosisResult.Success {
        val explanation = buildString {
            append("Excessive recomposition detected for \"${spike.composableName}\" ")
            append("(${spike.count} recompositions vs ${spike.baselineCount} baseline). ")
            append("This causes unnecessary UI work and can degrade scroll/animation performance.")
        }
        val suggestion = buildString {
            append("Common causes: reading unstable State in a frequently-recomposed scope, ")
            append("non-stable class parameters, or lambda captures creating new instances each composition. ")
            append("Try adding @Stable annotation to data classes, extracting lambdas to remember{}, ")
            append("or using derivedStateOf{} to reduce recomposition triggers.")
        }
        return DiagnosisResult.Success(
            regressionType = spike,
            explanation = explanation,
            suggestion = suggestion,
            confidence = FALLBACK_CONFIDENCE
        )
    }

    private fun diagnoseMemoryLeak(leak: RegressionType.MemoryLeak): DiagnosisResult.Success {
        val retainedKb = leak.retainedSizeBytes / 1024
        val explanation = buildString {
            append("Object of class \"${leak.retainedObjectClass}\" retained after expected lifecycle end ")
            append("(~${retainedKb}KB). This prevents garbage collection and increases memory pressure.")
        }
        val suggestion = buildString {
            append("Check for: static references to Activity/Context, unregistered listeners/callbacks, ")
            append("inner class holding outer class reference, or Handler/Runnable preventing GC. ")
            append("Use WeakReference for long-lived references to lifecycle-bound objects.")
        }
        return DiagnosisResult.Success(
            regressionType = leak,
            explanation = explanation,
            suggestion = suggestion,
            confidence = FALLBACK_CONFIDENCE
        )
    }

    private fun diagnoseSlowStartup(startup: RegressionType.SlowStartup): DiagnosisResult.Success {
        val explanation = buildString {
            append("Startup phase took ${startup.phaseMs}ms (threshold: ${startup.threshold}ms). ")
            append("Slow startup directly impacts user retention — users abandon apps that take >3s to load.")
        }
        val suggestion = buildString {
            append("Defer non-critical initialization using App Startup library or lazy loading. ")
            append("Move heavy operations off the main thread. ")
            append("Consider using Baseline Profiles to optimize ahead-of-time compilation.")
        }
        return DiagnosisResult.Success(
            regressionType = startup,
            explanation = explanation,
            suggestion = suggestion,
            confidence = FALLBACK_CONFIDENCE
        )
    }
}
