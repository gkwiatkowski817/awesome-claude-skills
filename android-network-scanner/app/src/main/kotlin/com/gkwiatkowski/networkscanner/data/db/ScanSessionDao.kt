package com.gkwiatkowski.networkscanner.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Insert
    suspend fun insert(session: ScanSessionEntity): Long

    @Update
    suspend fun update(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ScanSessionEntity>>

    @Query("SELECT * FROM scan_sessions ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSession(): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions WHERE id = :id")
    suspend fun getById(id: Long): ScanSessionEntity?

    @Query("DELETE FROM scan_sessions WHERE startTime < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("SELECT COUNT(*) FROM scan_sessions")
    suspend fun count(): Int
}
