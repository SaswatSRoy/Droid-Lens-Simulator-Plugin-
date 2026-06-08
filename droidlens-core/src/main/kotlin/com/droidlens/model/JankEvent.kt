package com.droidlens.model

/**
 * Event emitted when a jank frame is detected.
 */
data class JankEvent(
    val frameTimeMs: Float,
    val timestampMs: Long,
    val consecutiveJankCount: Int
)
