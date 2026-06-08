package com.droidlens.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.droidlens.db.entities.BaselineSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for baseline snapshots.
 */
@Dao
interface BaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(snapshot: BaselineSnapshot): Long

    @Query("SELECT * FROM baseline_snapshots WHERE sessionName = :sessionName ORDER BY capturedAtMs DESC LIMIT 1")
    suspend fun getLatestBaseline(sessionName: String): BaselineSnapshot?

    @Query("SELECT * FROM baseline_snapshots WHERE sessionName = :sessionName ORDER BY capturedAtMs DESC")
    suspend fun getAllBaselines(sessionName: String): List<BaselineSnapshot>

    @Query("DELETE FROM baseline_snapshots WHERE capturedAtMs < :timestampMs")
    suspend fun deleteBaselinesOlderThan(timestampMs: Long)

    @Query("SELECT * FROM baseline_snapshots WHERE sessionName = :sessionName ORDER BY capturedAtMs DESC LIMIT 1")
    fun observeLatestBaseline(sessionName: String): Flow<BaselineSnapshot?>
    
    @Query("DELETE FROM baseline_snapshots WHERE sessionName = :sessionName")
    suspend fun deleteBaselinesForSession(sessionName: String)
}
