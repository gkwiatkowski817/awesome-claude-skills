package com.gkwiatkowski.networkscanner.data.model

data class ScanSession(
    val id: Long = 0L,
    val startTime: Long,
    val endTime: Long? = null,
    val deviceCount: Int = 0,
    val wifiCount: Int = 0,
    val bluetoothCount: Int = 0,
    val networkCount: Int = 0,
    val triggeredBy: TriggerSource = TriggerSource.SCHEDULED
)

enum class TriggerSource { MANUAL, SCHEDULED, BOOT }
