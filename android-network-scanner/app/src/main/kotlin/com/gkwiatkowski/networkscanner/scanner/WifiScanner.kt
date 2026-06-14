package com.gkwiatkowski.networkscanner.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import com.gkwiatkowski.networkscanner.data.model.Device
import com.gkwiatkowski.networkscanner.data.model.DeviceType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class WifiScanner(private val context: Context) {
    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    suspend fun scan(): List<Device> {
        val results = withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        @Suppress("DEPRECATION")
                        cont.resume(wifiManager.scanResults ?: emptyList())
                    }
                }
                context.registerReceiver(
                    receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                )
                cont.invokeOnCancellation {
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                }
                @Suppress("DEPRECATION")
                if (!wifiManager.startScan()) {
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                    @Suppress("DEPRECATION")
                    cont.resume(wifiManager.scanResults ?: emptyList())
                }
            }
        } ?: run {
            @Suppress("DEPRECATION")
            wifiManager.scanResults ?: emptyList()
        }

        return results.map { it.toDevice() }
    }

    private fun ScanResult.toDevice(): Device {
        val ssid = SSID?.removePrefix("\"")?.removeSuffix("\"") ?: "Hidden Network"
        val freq = frequency
        val band = when {
            freq in 2400..2500 -> "2.4 GHz"
            freq in 5150..5850 -> "5 GHz"
            freq in 5925..7125 -> "6 GHz"
            else -> "Unknown"
        }
        return Device(
            id = BSSID ?: ssid,
            name = ssid.ifBlank { "Hidden Network" },
            macAddress = BSSID,
            type = DeviceType.WIFI_AP,
            signal = level,
            frequency = freq,
            capabilities = "$capabilities | $band | Ch${channelToNumber(freq)}",
            manufacturer = lookupOui(BSSID)
        )
    }

    private fun channelToNumber(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2407) / 5
        freq == 2484 -> 14
        freq in 5180..5825 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5955) / 5 + 1
        else -> 0
    }

    private fun lookupOui(mac: String?): String? {
        if (mac == null) return null
        val prefix = mac.uppercase().replace(":", "").take(6)
        return OUI_MAP[prefix]
    }

    companion object {
        private val OUI_MAP = mapOf(
            "FCFBFB" to "Apple", "F0D1B7" to "Apple", "3C0754" to "Apple",
            "B827EB" to "Raspberry Pi", "E45F01" to "Raspberry Pi",
            "00157D" to "Samsung", "94D7BC" to "Samsung", "A02195" to "Samsung",
            "001A2B" to "Cisco", "001BB1" to "Cisco", "0019AA" to "Cisco",
            "B4750E" to "ASUSTek", "04D4C4" to "ASUSTek",
            "C83A35" to "Netgear", "A40CC3" to "Netgear",
            "845B12" to "TP-Link", "B0A7B9" to "TP-Link",
            "201773" to "Google", "F4F5D8" to "Google",
            "3497F6" to "Amazon", "FC65DE" to "Amazon",
            "D01498" to "Sonos", "34170A" to "Sonos",
            "B8D812" to "Philips", "EC1BBD" to "Philips Hue"
        )
    }
}
