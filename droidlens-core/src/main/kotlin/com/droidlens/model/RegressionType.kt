package com.droidlens.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Types of performance regressions detected by Droid Lens.
 */
@Serializable
sealed class RegressionType {
    @Serializable
    @SerialName("JankFrame")
    data class JankFrame(val frameTimeMs: Long, val threshold: Long) : RegressionType()
    
    @Serializable
    @SerialName("RecompositionSpike")
    data class RecompositionSpike(
        val composableName: String, 
        val count: Int, 
        val baselineCount: Int
    ) : RegressionType()
    
    @Serializable
    @SerialName("MemoryLeak")
    data class MemoryLeak(
        val retainedObjectClass: String, 
        val retainedSizeBytes: Long
    ) : RegressionType()
    
    @Serializable
    @SerialName("SlowStartup")
    data class SlowStartup(val phaseMs: Long, val threshold: Long) : RegressionType()
}
