package com.gkwiatkowski.networkscanner.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gkwiatkowski.networkscanner.NetworkScannerApp
import com.gkwiatkowski.networkscanner.data.model.Device
import com.gkwiatkowski.networkscanner.data.model.DeviceType
import com.gkwiatkowski.networkscanner.data.model.ScanSession
import com.gkwiatkowski.networkscanner.data.model.TriggerSource
import com.gkwiatkowski.networkscanner.scanner.ScanManager
import com.gkwiatkowski.networkscanner.scanner.ScanStatus
import com.gkwiatkowski.networkscanner.service.BootReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NetworkScannerApp
    private val repository = app.repository
    private val scanManager = ScanManager(application)

    val scanStatus: StateFlow<ScanStatus> = scanManager.status

    val latestDevices: StateFlow<List<Device>> = repository.getLatestDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<ScanSession>> = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSessionDevices = MutableStateFlow<List<Device>>(emptyList())
    val selectedSessionDevices: StateFlow<List<Device>> = _selectedSessionDevices.asStateFlow()

    private val _deviceFilter = MutableStateFlow<DeviceType?>(null)
    val deviceFilter: StateFlow<DeviceType?> = _deviceFilter.asStateFlow()

    private val _backgroundScanEnabled = MutableStateFlow(false)
    val backgroundScanEnabled: StateFlow<Boolean> = _backgroundScanEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val filteredDevices: StateFlow<List<Device>> = combine(
        latestDevices, _deviceFilter
    ) { devices, filter ->
        if (filter == null) devices else devices.filter { it.type == filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startManualScan() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val sessionId = repository.startSession(TriggerSource.MANUAL)
                val devices = scanManager.runFullScan()
                repository.finishSession(sessionId, devices)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setDeviceFilter(type: DeviceType?) {
        _deviceFilter.value = type
    }

    fun toggleBackgroundScanning(enabled: Boolean) {
        _backgroundScanEnabled.value = enabled
        if (enabled) {
            BootReceiver.schedulePeriodicScan(getApplication())
        } else {
            BootReceiver.cancelPeriodicScan(getApplication())
        }
    }

    fun loadSessionDevices(sessionId: Long) {
        viewModelScope.launch {
            val devices = repository.getDevicesForSessionOnce(sessionId)
            _selectedSessionDevices.value = devices
        }
    }

    fun getDevicesBySubnet(devices: List<Device>): Map<String, List<Device>> {
        return devices
            .filter { it.ipAddress != null }
            .groupBy { it.subnet ?: extractSubnet(it.ipAddress!!) }
    }

    private fun extractSubnet(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.0/24" else ip
    }
}
