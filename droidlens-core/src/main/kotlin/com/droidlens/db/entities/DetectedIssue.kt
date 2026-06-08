package com.droidlens.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted performance regression issue detected during a session.
 */
@Entity(
    tableName = "detected_issues",
    indices = [Index(value = ["sessionName"])]
)
data class DetectedIssue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionName: String,
    val regressionType: String, // Serialized class name or type identifier
    val severity: String, // Severity enum name
    val detectedAtMs: Long,
    val rawDataJson: String,
    val llmExplanation: String? = null,
    val llmSuggestion: String? = null,
    val isResolved: Boolean = false
)
