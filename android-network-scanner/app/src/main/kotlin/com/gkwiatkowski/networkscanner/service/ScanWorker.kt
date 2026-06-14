package com.gkwiatkowski.networkscanner.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gkwiatkowski.networkscanner.NetworkScannerApp
import com.gkwiatkowski.networkscanner.data.model.TriggerSource
import com.gkwiatkowski.networkscanner.scanner.ScanManager

class ScanWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = context.applicationContext as NetworkScannerApp
            val repository = app.repository
            val scanManager = ScanManager(context)

            val sessionId = repository.startSession(TriggerSource.SCHEDULED)
            val devices = scanManager.runFullScan()
            repository.finishSession(sessionId, devices)

            repository.pruneOldSessions(keepDays = 30)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "periodic_network_scan"
    }
}
