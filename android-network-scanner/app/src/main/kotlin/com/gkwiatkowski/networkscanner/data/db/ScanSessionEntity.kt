package com.gkwiatkowski.networkscanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gkwiatkowski.networkscanner.data.model.ScanSession
import com.gkwiatkowski.networkscanner.data.model.TriggerSource

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val deviceCount: Int,
    val wifiCount: Int,
    val bluetoothCount: Int,
    val networkCount: Int,
    val triggeredBy: String
) {
    fun toSession() = ScanSession(
        id = id,
        startTime = startTime,
        endTime = endTime,
        deviceCount = deviceCount,
        wifiCount = wifiCount,
        bluetoothCount = bluetoothCount,
        networkCount = networkCount,
        triggeredBy = TriggerSource.valueOf(triggeredBy)
    )
}

fun ScanSession.toEntity() = ScanSessionEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    deviceCount = deviceCount,
    wifiCount = wifiCount,
    bluetoothCount = bluetoothCount,
    networkCount = networkCount,
    triggeredBy = triggeredBy.name
)
