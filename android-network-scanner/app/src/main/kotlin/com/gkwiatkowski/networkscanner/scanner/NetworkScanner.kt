package com.gkwiatkowski.networkscanner.scanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.gkwiatkowski.networkscanner.data.model.Device
import com.gkwiatkowski.networkscanner.data.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class SubnetInfo(
    val networkInterface: String,
    val ipAddress: String,
    val subnet: String,
    val gateway: String?
)

class NetworkScanner(private val context: Context) {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun getConnectedSubnets(): List<SubnetInfo> {
        val subnets = mutableListOf<SubnetInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return subnets
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.interfaceAddresses) {
                    val ip = addr.address ?: continue
                    if (ip.isLoopbackAddress || ip.isLinkLocalAddress) continue
                    if (ip.address.size != 4) continue  // IPv4 only
                    val ipStr = ip.hostAddress ?: continue
                    val prefix = addr.networkPrefixLength.toInt()
                    val subnetBase = calculateSubnet(ipStr, prefix)
                    subnets.add(SubnetInfo(
                        networkInterface = iface.name,
                        ipAddress = ipStr,
                        subnet = "$subnetBase/$prefix",
                        gateway = getGateway(iface.name)
                    ))
                }
            }
        } catch (_: Exception) {}
        return subnets
    }

    private fun calculateSubnet(ip: String, prefix: Int): String {
        val parts = ip.split(".").map { it.toInt() }
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val ipInt = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
        val netInt = ipInt and mask
        return "${(netInt shr 24) and 0xFF}.${(netInt shr 16) and 0xFF}.${(netInt shr 8) and 0xFF}.${netInt and 0xFF}"
    }

    private fun getGateway(ifaceName: String): String? {
        return try {
            BufferedReader(FileReader("/proc/net/route")).use { reader ->
                reader.lines().skip(1).filter { line ->
                    line.split("\t").firstOrNull() == ifaceName
                }.filter { line ->
                    line.split("\t").getOrNull(1) == "00000000"
                }.map { line ->
                    val hex = line.split("\t").getOrNull(2) ?: return@map null
                    parseHexIp(hex)
                }.filter { it != null && it != "0.0.0.0" }.findFirst().orElse(null)
            }
        } catch (_: Exception) { null }
    }

    private fun parseHexIp(hex: String): String? {
        return try {
            val value = hex.toLong(16)
            "${value and 0xFF}.${(value shr 8) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 24) and 0xFF}"
        } catch (_: Exception) { null }
    }

    suspend fun readArpTable(): List<Device> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<Device>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // skip header
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.trim().split(Regex("\\s+"))
                    if (parts.size < 4) continue
                    val ip = parts[0]
                    val mac = parts[3]
                    if (mac == "00:00:00:00:00:00" || mac.isBlank()) continue
                    val hostname = resolveHostname(ip)
                    devices.add(Device(
                        id = mac,
                        name = hostname ?: ip,
                        ipAddress = ip,
                        macAddress = mac,
                        type = DeviceType.NETWORK_HOST,
                        manufacturer = lookupOui(mac),
                        subnet = extractSubnet(ip)
                    ))
                }
            }
        } catch (_: Exception) {}
        devices
    }

    /**
     * Host discovery modelled on well-behaved LAN tools (e.g. Advanced IP Scanner):
     * a rate-limited ICMP-style "ping sweep" plus the OS ARP cache, instead of a
     * horizontal TCP port sweep. Hitting every address on many ports in parallel is
     * the reconnaissance pattern that IDS/firewalls flag; a paced liveness check is
     * ordinary network behaviour and is far lower impact. Service ports are only
     * touched on hosts already confirmed alive, never on the whole range.
     */
    suspend fun scanSubnet(subnet: SubnetInfo, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Device> =
        withContext(Dispatchers.IO) {
            val parts = subnet.ipAddress.split(".")
            if (parts.size != 4) return@withContext emptyList()
            val base = "${parts[0]}.${parts[1]}.${parts[2]}"
            val hostRange = 1..254
            val total = hostRange.count()
            val scanned = AtomicInteger(0)

            val arpDevices = readArpTable().associateBy { it.ipAddress }

            // Cap concurrency so probes leave the device in small batches rather than
            // a single burst across the whole subnet.
            val gate = Semaphore(DISCOVERY_CONCURRENCY)

            val jobs = hostRange.map { i ->
                async {
                    val ip = "$base.$i"
                    gate.withPermit {
                        val alive = arpDevices.containsKey(ip) || isHostAlive(ip)
                        onProgress(scanned.incrementAndGet(), total)
                        if (!alive) return@async null

                        // A successful ICMP probe primes the kernel ARP cache, so a
                        // re-read now usually yields the MAC for vendor lookup.
                        val mac = arpDevices[ip]?.macAddress ?: getMacFromArp(ip)
                        val hostname = resolveHostname(ip)
                        Device(
                            id = mac ?: ip,
                            name = hostname ?: ip,
                            ipAddress = ip,
                            macAddress = mac,
                            type = DeviceType.NETWORK_HOST,
                            manufacturer = mac?.let { lookupOui(it) },
                            subnet = extractSubnet(ip),
                            services = probeAliveHostServices(ip)
                        )
                    }
                }
            }
            jobs.awaitAll().filterNotNull()
        }

    /** Single ICMP-style liveness check (one packet), matching a normal ping. */
    private suspend fun isHostAlive(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName(ip).isReachable(ICMP_TIMEOUT_MS)
        } catch (_: Exception) { false }
    }

    /**
     * Light service hint, run ONLY against a host already confirmed alive and limited
     * to a couple of common web ports. This is normal client behaviour toward a known
     * host, not a subnet-wide port sweep.
     */
    private suspend fun probeAliveHostServices(ip: String): List<String> = withContext(Dispatchers.IO) {
        val services = mutableListOf<String>()
        for ((port, label) in LIGHT_SERVICE_PORTS) {
            try {
                Socket().use { it.connect(InetSocketAddress(ip, port), SERVICE_PROBE_TIMEOUT_MS) }
                services.add(label)
            } catch (_: Exception) {}
            delay(SERVICE_PROBE_SPACING_MS)
        }
        services
    }

    private fun resolveHostname(ip: String): String? {
        return try {
            val addr = InetAddress.getByName(ip)
            val hostname = addr.canonicalHostName
            if (hostname == ip) null else hostname
        } catch (_: Exception) { null }
    }

    private fun getMacFromArp(ip: String): String? {
        return try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.trim().split(Regex("\\s+"))
                    if (parts.firstOrNull() == ip && parts.size >= 4) {
                        val mac = parts[3]
                        if (mac != "00:00:00:00:00:00") return mac
                    }
                }
                null
            }
        } catch (_: Exception) { null }
    }

    suspend fun discoverMdnsServices(): List<Device> {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return emptyList()

        val found = mutableMapOf<String, Device>()
        val serviceTypes = listOf(
            "_http._tcp.", "_https._tcp.", "_ssh._tcp.", "_ftp._tcp.",
            "_smb._tcp.", "_afpovertcp._tcp.", "_sftp-ssh._tcp.",
            "_printer._tcp.", "_ipp._tcp.", "_pdl-datastream._tcp.",
            "_googlecast._tcp.", "_airplay._tcp.", "_raop._tcp.",
            "_homekit._tcp.", "_hap._tcp.", "_matter._tcp.",
            "_mqtt._tcp.", "_coap._tcp.", "_zigbee._tcp.",
            "_spotify-connect._tcp.", "_sonos._tcp."
        )

        for (serviceType in serviceTypes) {
            withTimeoutOrNull(3_000L) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : NsdManager.DiscoveryListener {
                        override fun onStartDiscoveryFailed(st: String, ec: Int) {
                            try { cont.resume(Unit) } catch (_: Exception) {}
                        }
                        override fun onStopDiscoveryFailed(st: String, ec: Int) {}
                        override fun onDiscoveryStarted(st: String) {}
                        override fun onDiscoveryStopped(st: String) {
                            try { cont.resume(Unit) } catch (_: Exception) {}
                        }
                        override fun onServiceFound(info: NsdServiceInfo) {
                            nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(i: NsdServiceInfo, ec: Int) {}
                                override fun onServiceResolved(i: NsdServiceInfo) {
                                    val ip = i.host?.hostAddress
                                    val key = "${i.serviceName}@${ip}"
                                    found[key] = Device(
                                        id = key,
                                        name = i.serviceName,
                                        ipAddress = ip,
                                        type = DeviceType.MDNS_SERVICE,
                                        subnet = ip?.let { extractSubnet(it) },
                                        services = listOf(i.serviceType),
                                        capabilities = i.attributes.keys.take(5).joinToString(", ")
                                    )
                                }
                            })
                        }
                        override fun onServiceLost(info: NsdServiceInfo) {}
                    }
                    nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                    cont.invokeOnCancellation {
                        try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
                    }
                }
            }
        }
        return found.values.toList()
    }

    private fun extractSubnet(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.0/24" else ip
    }

    private fun lookupOui(mac: String?): String? {
        if (mac == null) return null
        val prefix = mac.uppercase().replace(":", "").take(6)
        return OUI_MAP[prefix]
    }

    companion object {
        // Discovery is paced and ICMP-first to stay within ordinary LAN behaviour.
        private const val DISCOVERY_CONCURRENCY = 16
        private const val ICMP_TIMEOUT_MS = 600
        private const val SERVICE_PROBE_TIMEOUT_MS = 350
        private const val SERVICE_PROBE_SPACING_MS = 40L

        // Only a couple of common web ports, and only on hosts already alive.
        private val LIGHT_SERVICE_PORTS = listOf(80 to "HTTP", 443 to "HTTPS")

        private val OUI_MAP = mapOf(
            "FCFBFB" to "Apple", "F0D1B7" to "Apple", "3C0754" to "Apple",
            "B827EB" to "Raspberry Pi", "E45F01" to "Raspberry Pi",
            "00157D" to "Samsung", "94D7BC" to "Samsung",
            "001A2B" to "Cisco", "001BB1" to "Cisco",
            "C83A35" to "Netgear", "845B12" to "TP-Link",
            "201773" to "Google", "3497F6" to "Amazon",
            "D01498" to "Sonos", "B8D812" to "Philips Hue"
        )
    }
}
