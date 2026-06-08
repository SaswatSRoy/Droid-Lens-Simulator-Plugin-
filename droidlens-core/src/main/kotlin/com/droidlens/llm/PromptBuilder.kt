package com.droidlens.llm

import android.os.Build
import com.droidlens.model.Regression
import com.droidlens.model.RegressionType

/**
 * Constructs optimised system prompts for each [RegressionType] and parses LLM responses.
 *
 * This is the most critical class for output quality — each prompt is carefully structured
 * to produce consistent, actionable diagnoses from the Gemma 2B INT4 model.
 *
 * Response format expected from the LLM:
 * ```
 * CAUSE: [one sentence]
 * IMPACT: [one sentence]
 * FIX: [one specific fix]
 * CONFIDENCE: [HIGH/MEDIUM/LOW]
 * ```
 */
object PromptBuilder {

    /**
     * Builds a regression-type-specific prompt for the LLM.
     */
    fun buildPrompt(regression: Regression): String {
        return when (val type = regression.type) {
            is RegressionType.JankFrame -> buildJankFramePrompt(type, regression)
            is RegressionType.RecompositionSpike -> buildRecompositionSpikePrompt(type, regression)
            is RegressionType.MemoryLeak -> buildMemoryLeakPrompt(type, regression)
            is RegressionType.SlowStartup -> buildSlowStartupPrompt(type, regression)
        }
    }

    /**
     * Parses the structured LLM response into (explanation, suggestion).
     *
     * Explanation includes CAUSE + IMPACT lines.
     * Suggestion includes the FIX line.
     *
     * If parsing fails (malformed output), returns the raw output as explanation
     * with a generic suggestion message.
     */
    fun parseResponse(rawLlmOutput: String): Pair<String, String> {
        val trimmed = rawLlmOutput.trim()

        val cause = extractField(trimmed, "CAUSE")
        val impact = extractField(trimmed, "IMPACT")
        val fix = extractField(trimmed, "FIX")

        // If we couldn't parse at least CAUSE and FIX, return raw output
        if (cause == null && fix == null) {
            return Pair(trimmed, "See raw LLM output above for details.")
        }

        val explanation = buildString {
            if (cause != null) {
                append("Cause: $cause")
            }
            if (impact != null) {
                if (isNotEmpty()) append("\n")
                append("Impact: $impact")
            }
        }.ifBlank { trimmed }

        val suggestion = if (fix != null) {
            "Fix: $fix"
        } else {
            "See raw LLM output for suggestions."
        }

        return Pair(explanation, suggestion)
    }

    /**
     * Extracts the confidence level from the LLM response as a float (0.0 - 1.0).
     *
     * Maps: HIGH → 0.9, MEDIUM → 0.6, LOW → 0.3
     * Returns 0.5 if parsing fails.
     */
    fun parseConfidence(rawLlmOutput: String): Float {
        val confidence = extractField(rawLlmOutput.trim(), "CONFIDENCE")
            ?.uppercase()
            ?.trim()

        return when {
            confidence?.contains("HIGH") == true -> 0.9f
            confidence?.contains("MEDIUM") == true -> 0.6f
            confidence?.contains("LOW") == true -> 0.3f
            else -> 0.5f
        }
    }

    // ---- Private prompt builders ----

    private fun buildJankFramePrompt(jank: RegressionType.JankFrame, regression: Regression): String {
        val baselineAvg = extractJsonField(regression.rawData, "baselineAvgMs") ?: "unknown"
        val consecutiveCount = extractJsonField(regression.rawData, "consecutiveCount") ?: "1"

        return """
            |You are an Android performance expert. Analyze this frame timing data and provide a concise diagnosis.
            |
            |Data: ${jank.frameTimeMs}ms frame detected (threshold: ${jank.threshold}ms).
            |Session baseline average: ${baselineAvg}ms.
            |Consecutive jank frames: $consecutiveCount.
            |Device: ${Build.MODEL}, Android ${Build.VERSION.SDK_INT}.
            |
            |Respond in this exact format:
            |CAUSE: [one sentence explaining the most likely cause]
            |IMPACT: [one sentence on user experience impact]
            |FIX: [one specific, actionable code change]
            |CONFIDENCE: [HIGH/MEDIUM/LOW]
        """.trimMargin()
    }

    private fun buildRecompositionSpikePrompt(
        spike: RegressionType.RecompositionSpike,
        regression: Regression
    ): String {
        val spikeRatio = if (spike.baselineCount > 0) {
            "%.1f".format(spike.count.toFloat() / spike.baselineCount.toFloat())
        } else {
            "N/A"
        }

        return """
            |You are a Jetpack Compose performance expert. Analyze this recomposition data.
            |
            |Data: Composable "${spike.composableName}" recomposed ${spike.count} times in 1 second.
            |Baseline: ${spike.baselineCount} recompositions/second.
            |Spike ratio: ${spikeRatio}x above baseline.
            |
            |Respond in this exact format:
            |CAUSE: [one sentence — most likely: unstable state, non-stable class, lambda capture]
            |IMPACT: [one sentence]
            |FIX: [one specific fix — e.g., "Add @Stable annotation to X" or "Extract Y to remember{}"]
            |CONFIDENCE: [HIGH/MEDIUM/LOW]
        """.trimMargin()
    }

    private fun buildMemoryLeakPrompt(
        leak: RegressionType.MemoryLeak,
        regression: Regression
    ): String {
        val retainedSeconds = extractJsonField(regression.rawData, "retainedSeconds") ?: "unknown"
        val retainedKb = leak.retainedSizeBytes / 1024
        val description = extractJsonField(regression.rawData, "description")
            ?: "No additional context"

        return """
            |You are an Android memory management expert.
            |
            |Data: Object of class "${leak.retainedObjectClass}" has been retained for ${retainedSeconds}s after expected release. Estimated size: ${retainedKb}KB.
            |Context: $description
            |
            |Respond in this exact format:
            |CAUSE: [most likely retention cause: static reference, inner class, callback not removed]
            |IMPACT: [memory impact]
            |FIX: [specific code fix]
            |CONFIDENCE: [HIGH/MEDIUM/LOW]
        """.trimMargin()
    }

    private fun buildSlowStartupPrompt(
        startup: RegressionType.SlowStartup,
        regression: Regression
    ): String {
        return """
            |You are an Android startup performance expert.
            |
            |Data: Startup phase took ${startup.phaseMs}ms (threshold: ${startup.threshold}ms).
            |Device: ${Build.MODEL}, Android ${Build.VERSION.SDK_INT}.
            |
            |Respond in this exact format:
            |CAUSE: [one sentence explaining the most likely cause of slow startup]
            |IMPACT: [one sentence on user experience impact]
            |FIX: [one specific, actionable optimization]
            |CONFIDENCE: [HIGH/MEDIUM/LOW]
        """.trimMargin()
    }

    // ---- Utility functions ----

    /**
     * Extracts a field value from the structured LLM response.
     * Looks for lines like "CAUSE: some text here"
     */
    private fun extractField(text: String, fieldName: String): String? {
        // Match "FIELDNAME:" at the start of a line, capture everything after it
        val regex = Regex("""(?m)^$fieldName:\s*(.+)$""")
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Simple JSON field extractor — avoids pulling in a JSON parser for simple key-value extraction.
     * Works for flat JSON like {"key": "value", "key2": 123}
     */
    private fun extractJsonField(json: String, fieldName: String): String? {
        // Match "fieldName": "value" or "fieldName": 123
        val stringRegex = Regex(""""$fieldName"\s*:\s*"([^"]*?)"""")
        val stringMatch = stringRegex.find(json)
        if (stringMatch != null) {
            return stringMatch.groupValues[1]
        }

        // Try numeric value
        val numericRegex = Regex(""""$fieldName"\s*:\s*([0-9.]+)""")
        val numericMatch = numericRegex.find(json)
        if (numericMatch != null) {
            return numericMatch.groupValues[1]
        }

        return null
    }
}
