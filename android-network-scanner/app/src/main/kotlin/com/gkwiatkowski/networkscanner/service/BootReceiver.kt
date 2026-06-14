package com.gkwiatkowski.networkscanner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            schedulePeriodicScan(context)
        }
    }

    companion object {
        fun schedulePeriodicScan(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScanWorker>(10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                ScanWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodicScan(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(ScanWorker.WORK_NAME)
        }
    }
}
