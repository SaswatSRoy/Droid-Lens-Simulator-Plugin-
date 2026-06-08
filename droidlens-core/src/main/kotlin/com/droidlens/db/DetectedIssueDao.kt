package com.droidlens.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.droidlens.db.entities.DetectedIssue
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for detected performance issues.
 */
@Dao
interface DetectedIssueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(issue: DetectedIssue): Long

    @Query("UPDATE detected_issues SET llmExplanation = :explanation, llmSuggestion = :suggestion WHERE id = :id")
    suspend fun updateLlmDiagnosis(id: Long, explanation: String, suggestion: String)

    @Query("SELECT * FROM detected_issues WHERE llmExplanation IS NULL")
    suspend fun getUndiagnosedIssues(): List<DetectedIssue>

    @Query("SELECT * FROM detected_issues WHERE sessionName = :sessionName ORDER BY detectedAtMs DESC LIMIT :limit")
    fun observeRecentIssues(sessionName: String, limit: Int): Flow<List<DetectedIssue>>

    @Query("UPDATE detected_issues SET isResolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Long)
}
