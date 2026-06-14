package com.gkwiatkowski.networkscanner.data.model

data class Device(
    val id: String,
    val name: String,
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val type: DeviceType,
    val manufacturer: String? = null,
    val signal: Int? = null,
    val frequency: Int? = null,
    val capabilities: String? = null,
    val subnet: String? = null,
    val services: List<String> = emptyList(),
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val sessionId: Long = 0L
)
