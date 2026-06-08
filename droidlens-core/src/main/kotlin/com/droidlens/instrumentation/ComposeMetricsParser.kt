package com.droidlens.instrumentation

import java.io.File

/**
 * Parsed metric for a single composable from compiler metrics.
 */
data class ComposableMetric(
    val composableName: String,
    val isRestartable: Boolean,
    val isSkippable: Boolean,
    val recomposeCount: Int
)

/**
 * Parses Compose compiler metrics files (*-composables.csv).
 */
class ComposeMetricsParser {
    private var lastModificationTime: Long = -1
    private var cachedMetrics: List<ComposableMetric> = emptyList()

    /**
     * Parses the CSV file if it exists and has changed.
     */
    fun parseMetrics(metricsPath: String): List<ComposableMetric> {
        val file = File(metricsPath)
        if (!file.exists()) return emptyList()

        val currentTime = file.lastModified()
        if (currentTime == lastModificationTime) {
            return cachedMetrics
        }

        return try {
            val lines = file.readLines()
            if (lines.isEmpty()) return emptyList()

            // Assume header: composableName,isRestartable,isSkippable,recomposeCount
            val metrics = lines.drop(1).mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size >= 4) {
                    ComposableMetric(
                        composableName = parts[0].trim(),
                        isRestartable = parts[1].trim().toBoolean(),
                        isSkippable = parts[2].trim().toBoolean(),
                        recomposeCount = parts[3].trim().toIntOrNull() ?: 0
                    )
                } else {
                    null
                }
            }

            lastModificationTime = currentTime
            cachedMetrics = metrics
            metrics
        } catch (e: Exception) {
            emptyList()
        }
    }
}
