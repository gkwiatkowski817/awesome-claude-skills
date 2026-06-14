package com.gkwiatkowski.networkscanner.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<DeviceEntity>)

    @Query("SELECT * FROM devices WHERE sessionId = :sessionId ORDER BY type, name")
    fun getDevicesForSession(sessionId: Long): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE sessionId = :sessionId ORDER BY type, name")
    suspend fun getDevicesForSessionOnce(sessionId: Long): List<DeviceEntity>

    @Query("""
        SELECT * FROM devices
        WHERE sessionId = (SELECT MAX(id) FROM scan_sessions)
        ORDER BY type, name
    """)
    fun getLatestSessionDevices(): Flow<List<DeviceEntity>>

    @Query("""
        SELECT DISTINCT d.* FROM devices d
        WHERE d.macAddress IS NOT NULL
        GROUP BY d.macAddress
        ORDER BY d.lastSeen DESC
    """)
    fun getAllUniqueDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT COUNT(*) FROM devices WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("DELETE FROM devices WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("SELECT * FROM devices WHERE sessionId = :sessionId AND subnet = :subnet")
    suspend fun getDevicesInSubnet(sessionId: Long, subnet: String): List<DeviceEntity>
}
