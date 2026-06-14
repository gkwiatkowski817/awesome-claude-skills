package com.gkwiatkowski.networkscanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gkwiatkowski.networkscanner.MainActivity
import com.gkwiatkowski.networkscanner.NetworkScannerApp
import com.gkwiatkowski.networkscanner.data.model.TriggerSource
import com.gkwiatkowski.networkscanner.scanner.ScanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScanForegroundService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting scan…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trigger = intent?.getStringExtra(EXTRA_TRIGGER)
            ?.let { TriggerSource.valueOf(it) } ?: TriggerSource.MANUAL

        scope.launch {
            val app = application as NetworkScannerApp
            val repository = app.repository
            val scanManager = ScanManager(applicationContext)

            val sessionId = repository.startSession(trigger)
            updateNotification("Scanning network…")

            scanManager.status.collect { status ->
                when (status) {
                    is com.gkwiatkowski.networkscanner.scanner.ScanStatus.Scanning ->
                        updateNotification(status.phase)
                    is com.gkwiatkowski.networkscanner.scanner.ScanStatus.Complete -> {
                        updateNotification("Scan complete: ${status.deviceCount} devices")
                        return@collect
                    }
                    else -> {}
                }
            }
        }

        scope.launch {
            val app = application as NetworkScannerApp
            val scanManager = ScanManager(applicationContext)
            val devices = scanManager.runFullScan()
            app.repository.finishSession(0, devices)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Network Scanner",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background network scanning"
            setShowBadge(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Network Scanner")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val CHANNEL_ID = "network_scanner_service"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_TRIGGER = "trigger_source"
    }
}
