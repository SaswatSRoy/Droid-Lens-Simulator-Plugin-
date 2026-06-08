package com.droidlens.model

/**
 * Real-time frame performance metrics.
 */
data class FrameMetrics(
    val currentFps: Float,
    val avgFrameTimeMs: Float,
    val p95FrameTimeMs: Float,
    val jankFrameCount: Int,
    val isJanking: Boolean
) {
    companion object {
        fun empty() = FrameMetrics(0f, 0f, 0f, 0, false)
    }
}
