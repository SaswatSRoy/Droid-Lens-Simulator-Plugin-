package com.droidlens.model

/**
 * Represents a spike in recomposition count for a specific composable.
 */
data class RecompositionSpike(
    val composableName: String,
    val currentCount: Int,
    val baselineCount: Int,
    val spikeRatio: Float,
    val windowMs: Long = 1000L
)
