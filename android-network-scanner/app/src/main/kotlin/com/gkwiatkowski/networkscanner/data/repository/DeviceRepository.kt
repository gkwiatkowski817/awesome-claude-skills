package com.gkwiatkowski.networkscanner.data.repository

import com.gkwiatkowski.networkscanner.data.db.AppDatabase
import com.gkwiatkowski.networkscanner.data.db.toEntity
import com.gkwiatkowski.networkscanner.data.model.Device
import com.gkwiatkowski.networkscanner.data.model.ScanSession
import com.gkwiatkowski.networkscanner.data.model.TriggerSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class DeviceRepository(private val db: AppDatabase) {
    private val deviceDao = db.deviceDao()
    private val sessionDao = db.scanSessionDao()

    suspend fun startSession(trigger: TriggerSource): Long {
        val entity = ScanSession(
            startTime = System.currentTimeMillis(),
            triggeredBy = trigger
        ).toEntity()
        return sessionDao.insert(entity)
    }

    suspend fun finishSession(sessionId: Long, devices: List<Device>) {
        val session = sessionDao.getById(sessionId) ?: return
        val wifi = devices.count { it.type.name.startsWith("WIFI") }
        val bt = devices.count { it.type.name.startsWith("BT") }
        val net = devices.count { it.type.name == "NETWORK_HOST" || it.type.name == "MDNS_SERVICE" }
        sessionDao.update(session.copy(
            endTime = System.currentTimeMillis(),
            deviceCount = devices.size,
            wifiCount = wifi,
            bluetoothCount = bt,
            networkCount = net
        ))
        deviceDao.insertAll(devices.map { it.copy(sessionId = sessionId).toEntity() })
    }

    fun getLatestDevices(): Flow<List<Device>> =
        deviceDao.getLatestSessionDevices().map { list -> list.map { it.toDevice() } }

    fun getAllSessions(): Flow<List<ScanSession>> =
        sessionDao.getAllSessions().map { list -> list.map { it.toSession() } }

    fun getDevicesForSession(sessionId: Long): Flow<List<Device>> =
        deviceDao.getDevicesForSession(sessionId).map { list -> list.map { it.toDevice() } }

    suspend fun getDevicesForSessionOnce(sessionId: Long): List<Device> =
        deviceDao.getDevicesForSessionOnce(sessionId).map { it.toDevice() }

    fun getAllUniqueDevices(): Flow<List<Device>> =
        deviceDao.getAllUniqueDevices().map { list -> list.map { it.toDevice() } }

    suspend fun pruneOldSessions(keepDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepDays.toLong())
        sessionDao.deleteOlderThan(cutoff)
    }
}
