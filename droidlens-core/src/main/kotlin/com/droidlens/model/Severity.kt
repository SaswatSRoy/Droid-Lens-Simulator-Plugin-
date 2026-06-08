package com.droidlens.model

import kotlinx.serialization.Serializable

/**
 * Severity level of a detected performance regression.
 */
@Serializable
enum class Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}
