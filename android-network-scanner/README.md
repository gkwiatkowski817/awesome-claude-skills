# Android Network Scanner

A comprehensive Android network scanner app for Samsung Galaxy S21 and other Android devices. Scans WiFi networks, Bluetooth devices (Classic + BLE), and all hosts on connected IP subnets. Runs in the background every 10 minutes and maintains a full history of discovered devices.

## Features

### Scanning
- **Wi-Fi Networks** — Discovers all nearby 802.11 access points (2.4/5/6 GHz), with SSID, BSSID, signal strength, channel, and security capabilities
- **Bluetooth Classic** — Discovers nearby Bluetooth Classic devices via inquiry scan
- **Bluetooth LE** — Discovers BLE devices with advertised service UUIDs
- **Network Hosts** — Scans all connected subnets via ARP table + TCP port probing across all `/24` ranges
- **mDNS/Bonjour** — Discovers networked services: printers, Chromecast, AirPlay, HomeKit, MQTT, CoAP, Sonos, and 15+ service types
- **Cross-subnet** — Enumerates all active network interfaces and scans each subnet independently

### Background Operation
- Periodic scan every **10 minutes** using WorkManager (battery-efficient)
- Foreground service with persistent notification during active scans
- Auto-restarts after device reboot

### History
- Full scan session history with timestamps, duration, and device counts
- Per-session device breakdown (Wi-Fi / Bluetooth / Network)
- Auto-prunes sessions older than 30 days

### UI (4 tabs)
| Tab | Description |
|-----|-------------|
| **Dashboard** | Scan control, live status, quick stats, background toggle |
| **Devices** | Searchable/filterable device list with expandable details |
| **Network Map** | Interactive topology canvas + per-subnet host table |
| **History** | Past sessions with expandable device lists |

## Technical Details

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material Design 3 with dynamic color
- **Architecture**: MVVM + Repository pattern
- **Database**: Room (SQLite) — devices + sessions persisted locally
- **Background**: WorkManager periodic task + ForegroundService
- **Concurrency**: Kotlin Coroutines + StateFlow

## Scanning Limits (Hardware Constraints)

| Protocol | S21 Support | Notes |
|----------|------------|-------|
| Wi-Fi 802.11 | ✅ Full | 2.4/5/6 GHz via WifiManager |
| Bluetooth Classic | ✅ Full | Via BluetoothAdapter.startDiscovery() |
| Bluetooth LE | ✅ Full | Via BluetoothLeScanner |
| IP Network Hosts | ✅ Full | ARP table + TCP probe |
| mDNS/Bonjour | ✅ Full | Via NsdManager |
| Zigbee | ❌ None | No Zigbee radio in S21 — detected indirectly via mDNS if hub is present |
| Z-Wave | ❌ None | No Z-Wave radio in S21 |
| Thread/Matter | ⚠️ Partial | Detected via mDNS `_matter._tcp` if devices advertise |
| NFC | ❌ Not scanned | Short-range only, no scanning |

## Permissions Required

- `ACCESS_FINE_LOCATION` — Required by Android for Wi-Fi scan results
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` — Android 12+ Bluetooth scanning
- `INTERNET` + `ACCESS_NETWORK_STATE` — Network host scanning
- `FOREGROUND_SERVICE` — Background scanning notification
- `POST_NOTIFICATIONS` — Android 13+ notification permission
- `RECEIVE_BOOT_COMPLETED` — Restart periodic scan after reboot

## Setup

1. Clone or download the project
2. Open in Android Studio Koala (2024.1.1) or later
3. Sync Gradle
4. Build and install on device
5. Grant all requested permissions (especially Location — required for Wi-Fi scanning)
6. Tap the scan button on the Dashboard to run your first scan

## Building

```bash
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`.
