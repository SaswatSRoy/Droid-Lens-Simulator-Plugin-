package com.droidlens.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.droidlens.BuildConfig

/**
 * Persisted snapshot of performance metrics to serve as a baseline.
 */
@Entity(
    tableName = "baseline_snapshots",
    indices = [Index(value = ["sessionName"])]
)
data class BaselineSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionName: String,
    val capturedAtMs: Long,
    val avgFrameTimeMs: Float,
    val p95FrameTimeMs: Float,
    val jankFrameCount: Int,
    val composableRecompositions: String, // JSON: Map<String, Int>
    val usedMemoryMb: Float,
    val maxMemoryMb: Float,
    val startupTimeMs: Long,
    val appVersion: String = BuildConfig.VERSION_NAME
)
