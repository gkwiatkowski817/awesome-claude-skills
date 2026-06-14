package com.gkwiatkowski.networkscanner

import android.app.Application
import com.gkwiatkowski.networkscanner.data.db.AppDatabase
import com.gkwiatkowski.networkscanner.data.repository.DeviceRepository

class NetworkScannerApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { DeviceRepository(database) }
}
