package com.droidlens.model

import kotlinx.serialization.Serializable

/**
 * A comprehensive report for a profiling session.
 */
@Serializable
data class SessionReport(
    val sessionName: String,
    val durationMs: Long,
    val totalJankFrames: Int,
    val avgFrameTimeMs: Float,
    val recompositionSpikes: List<RegressionType.RecompositionSpike>,
    val memoryLeaks: List<RegressionType.MemoryLeak>,
    val detectedRegressions: List<Regression>,
    val llmDiagnoses: List<DiagnosisResult>,
    val exportedJsonPath: String?
)
