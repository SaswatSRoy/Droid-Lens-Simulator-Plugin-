package com.droidlens.model

/**
 * Event emitted when an object is potentially leaked.
 */
data class MemoryLeakEvent(
    val watchKey: String,
    val description: String,
    val retainedSince: Long,
    val estimatedRetainedBytes: Long
)
