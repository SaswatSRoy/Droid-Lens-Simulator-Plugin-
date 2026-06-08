package com.droidlens.model

/**
 * Real-time heap memory metrics.
 */
data class HeapMetrics(
    val usedMemoryMb: Float,
    val maxMemoryMb: Float,
    val usagePercent: Float,
    val watchedObjectCount: Int,
    val retainedObjectCount: Int
) {
    companion object {
        fun empty() = HeapMetrics(0f, 0f, 0f, 0, 0)
    }
}
