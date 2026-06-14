package com.gkwiatkowski.networkscanner.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gkwiatkowski.networkscanner.data.model.Device
import com.gkwiatkowski.networkscanner.data.model.DeviceType

@Entity(
    tableName = "devices",
    foreignKeys = [ForeignKey(
        entity = ScanSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("macAddress"), Index("ipAddress")]
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val dbId: Long = 0,
    val id: String,
    val name: String,
    val ipAddress: String?,
    val macAddress: String?,
    val type: String,
    val manufacturer: String?,
    val signal: Int?,
    val frequency: Int?,
    val capabilities: String?,
    val subnet: String?,
    val services: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val sessionId: Long
) {
    fun toDevice() = Device(
        id = id,
        name = name,
        ipAddress = ipAddress,
        macAddress = macAddress,
        type = DeviceType.valueOf(type),
        manufacturer = manufacturer,
        signal = signal,
        frequency = frequency,
        capabilities = capabilities,
        subnet = subnet,
        services = if (services.isBlank()) emptyList() else services.split("|"),
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        sessionId = sessionId
    )
}

fun Device.toEntity() = DeviceEntity(
    id = id,
    name = name,
    ipAddress = ipAddress,
    macAddress = macAddress,
    type = type.name,
    manufacturer = manufacturer,
    signal = signal,
    frequency = frequency,
    capabilities = capabilities,
    subnet = subnet,
    services = services.joinToString("|"),
    firstSeen = firstSeen,
    lastSeen = lastSeen,
    sessionId = sessionId
)
