package com.gkwiatkowski.networkscanner.scanner

import android.content.Context
import com.gkwiatkowski.networkscanner.data.model.Device
import com.gkwiatkowski.networkscanner.data.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed class ScanStatus {
    object Idle : ScanStatus()
    data class Scanning(val phase: String, val progress: Int = 0) : ScanStatus()
    data class Complete(val deviceCount: Int) : ScanStatus()
    data class Error(val message: String) : ScanStatus()
}

class ScanManager(private val context: Context) {
    private val wifiScanner = WifiScanner(context)
    private val bluetoothScanner = BluetoothScanner(context)
    private val networkScanner = NetworkScanner(context)

    private val _status = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val status: StateFlow<ScanStatus> = _status.asStateFlow()

    suspend fun runFullScan(): List<Device> = coroutineScope {
        val allDevices = mutableListOf<Device>()

        _status.value = ScanStatus.Scanning("Scanning Wi-Fi networks...")
        val wifiJob = async(Dispatchers.IO) {
            runCatching { wifiScanner.scan() }.getOrDefault(emptyList())
        }

        _status.value = ScanStatus.Scanning("Scanning Bluetooth devices...")
        val bleJob = async(Dispatchers.IO) {
            runCatching { bluetoothScanner.scanBle() }.getOrDefault(emptyList())
        }

        val classicJob = async(Dispatchers.IO) {
            runCatching { bluetoothScanner.scanClassic() }.getOrDefault(emptyList())
        }

        _status.value = ScanStatus.Scanning("Reading network ARP table...")
        val arpDevices = withContext(Dispatchers.IO) {
            runCatching { networkScanner.readArpTable() }.getOrDefault(emptyList())
        }

        _status.value = ScanStatus.Scanning("Discovering mDNS services...")
        val mdnsJob = async(Dispatchers.IO) {
            runCatching { networkScanner.discoverMdnsServices() }.getOrDefault(emptyList())
        }

        val wifiResults = wifiJob.await()
        allDevices.addAll(wifiResults)
        allDevices.addAll(arpDevices)
        allDevices.addAll(mdnsJob.await())

        _status.value = ScanStatus.Scanning("Scanning network subnets...")
        val subnets = networkScanner.getConnectedSubnets()
        var totalHosts = 0
        val subnetResults = subnets.flatMap { subnet ->
            networkScanner.scanSubnet(subnet) { scanned, total ->
                totalHosts = total
                val progress = if (total > 0) (scanned * 100) / total else 0
                _status.value = ScanStatus.Scanning(
                    "Scanning ${subnet.subnet} ($scanned/$total)",
                    progress
                )
            }
        }
        allDevices.addAll(subnetResults)

        val bleResults = bleJob.await()
        val classicResults = classicJob.await()
        allDevices.addAll(bleResults)
        allDevices.addAll(classicResults)

        val deduped = deduplicateDevices(allDevices)
        _status.value = ScanStatus.Complete(deduped.size)
        deduped
    }

    private fun deduplicateDevices(devices: List<Device>): List<Device> {
        val byMac = mutableMapOf<String, Device>()
        val byIp = mutableMapOf<String, Device>()
        val unique = mutableListOf<Device>()

        for (device in devices) {
            val mac = device.macAddress
            val ip = device.ipAddress

            when {
                mac != null && byMac.containsKey(mac) -> {
                    val existing = byMac[mac]!!
                    if (device.type == DeviceType.NETWORK_HOST && existing.type == DeviceType.WIFI_AP) {
                        byMac[mac] = existing.copy(ipAddress = ip ?: existing.ipAddress)
                    }
                }
                ip != null && byIp.containsKey(ip) -> {
                    val existing = byIp[ip]!!
                    if (mac != null && existing.macAddress == null) {
                        val merged = existing.copy(macAddress = mac, manufacturer = device.manufacturer)
                        byIp[ip] = merged
                        mac?.let { byMac[it] = merged }
                    }
                }
                else -> {
                    unique.add(device)
                    mac?.let { byMac[it] = device }
                    ip?.let { byIp[it] = device }
                }
            }
        }

        return (byMac.values + byIp.values.filter { it.macAddress == null }).distinctBy { it.id }
    }
}
