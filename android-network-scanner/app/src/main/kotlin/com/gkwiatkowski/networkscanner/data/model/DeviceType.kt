package com.gkwiatkowski.networkscanner.data.model

enum class DeviceType(val label: String) {
    WIFI_AP("Wi-Fi AP"),
    WIFI_CLIENT("Wi-Fi Client"),
    BT_CLASSIC("Bluetooth"),
    BT_LE("Bluetooth LE"),
    NETWORK_HOST("Network Host"),
    MDNS_SERVICE("mDNS Service"),
    ZIGBEE("Zigbee"),
    THREAD("Thread/Matter"),
    UNKNOWN("Unknown")
}
