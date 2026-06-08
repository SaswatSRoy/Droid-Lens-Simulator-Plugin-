package com.droidlens.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Result of an LLM diagnosis for a regression.
 */
@Serializable
sealed class DiagnosisResult {
    @Serializable
    @SerialName("Success")
    data class Success(
        val regressionType: RegressionType,
        val explanation: String,
        val suggestion: String,
        val confidence: Float
    ) : DiagnosisResult()

    @Serializable
    @SerialName("Failure")
    data class Failure(
        val regressionType: RegressionType,
        val error: String
    ) : DiagnosisResult()

    @Serializable
    @SerialName("Pending")
    data class Pending(val regressionType: RegressionType) : DiagnosisResult()
}
