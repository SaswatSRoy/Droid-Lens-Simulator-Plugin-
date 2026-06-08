package com.droidlens.model

import kotlinx.serialization.Serializable

/**
 * Represents a detected performance regression.
 */
@Serializable
data class Regression(
    val type: RegressionType,
    val severity: Severity,
    val detectedAtMs: Long,
    val sessionName: String,
    val rawData: String
)
