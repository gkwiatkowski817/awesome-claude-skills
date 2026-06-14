package com.gkwiatkowski.networkscanner.scanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gkwiatkowski.networkscanner.data.model.Device
import com.gkwiatkowski.networkscanner.data.model.DeviceType
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class BluetoothScanner(private val context: Context) {
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = btManager?.adapter

    fun isAvailable() = adapter != null && adapter.isEnabled

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    suspend fun scanClassic(): List<Device> {
        if (!isAvailable() || !hasScanPermission()) return emptyList()

        val found = mutableMapOf<String, Device>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        } ?: return
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        val address = device.address ?: return
                        val name = if (hasConnectPermission()) {
                            device.name ?: "Unknown BT Device"
                        } else "Unknown BT Device"
                        found[address] = Device(
                            id = address,
                            name = name,
                            macAddress = address,
                            type = DeviceType.BT_CLASSIC,
                            signal = rssi,
                            manufacturer = lookupOui(address)
                        )
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)

        try {
            @Suppress("DEPRECATION")
            adapter?.startDiscovery()
            delay(12_000L)
            @Suppress("DEPRECATION")
            adapter?.cancelDiscovery()
        } finally {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }

        return found.values.toList()
    }

    suspend fun scanBle(): List<Device> {
        if (!isAvailable() || !hasScanPermission()) return emptyList()
        val leScanner = adapter?.bluetoothLeScanner ?: return emptyList()

        val found = mutableMapOf<String, Device>()

        val results = withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val device = result.device
                        val address = device.address ?: return
                        val name = if (hasConnectPermission()) {
                            result.scanRecord?.deviceName ?: device.name ?: "BLE Device"
                        } else "BLE Device"
                        found[address] = Device(
                            id = address,
                            name = name,
                            macAddress = address,
                            type = DeviceType.BT_LE,
                            signal = result.rssi,
                            manufacturer = lookupOui(address),
                            capabilities = result.scanRecord?.serviceUuids
                                ?.joinToString(", ") { it.uuid.toString().take(8) }
                        )
                    }

                    override fun onScanFailed(errorCode: Int) {
                        try { cont.resume(Unit) } catch (_: Exception) {}
                    }
                }

                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                leScanner.startScan(null, settings, callback)

                cont.invokeOnCancellation {
                    try { leScanner.stopScan(callback) } catch (_: Exception) {}
                }
            }
        }

        try { leScanner.stopScan(object : ScanCallback() {}) } catch (_: Exception) {}
        return found.values.toList()
    }

    private fun lookupOui(mac: String?): String? {
        if (mac == null) return null
        val prefix = mac.uppercase().replace(":", "").take(6)
        return OUI_MAP[prefix]
    }

    companion object {
        private val OUI_MAP = mapOf(
            "FCFBFB" to "Apple", "F0D1B7" to "Apple", "3C0754" to "Apple",
            "00157D" to "Samsung", "94D7BC" to "Samsung",
            "201773" to "Google", "3C5AB4" to "Google",
            "3497F6" to "Amazon", "68376D" to "Amazon Echo",
            "D01498" to "Sonos", "A8BB50" to "Apple AirPods",
            "B0D5CC" to "Xiaomi", "F8A2D6" to "OnePlus",
            "DC0C5C" to "Garmin", "009065" to "Jabra"
        )
    }
}
