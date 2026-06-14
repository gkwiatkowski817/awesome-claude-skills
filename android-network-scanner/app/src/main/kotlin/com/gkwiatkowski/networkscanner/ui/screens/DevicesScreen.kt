package com.gkwiatkowski.networkscanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gkwiatkowski.networkscanner.data.model.Device
import com.gkwiatkowski.networkscanner.data.model.DeviceType
import com.gkwiatkowski.networkscanner.ui.MainViewModel
import com.gkwiatkowski.networkscanner.ui.theme.DeviceColors

@Composable
fun DevicesScreen(viewModel: MainViewModel) {
    val devices by viewModel.latestDevices.collectAsState()
    val activeFilter by viewModel.deviceFilter.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var expandedDeviceId by remember { mutableStateOf<String?>(null) }

    val filtered = devices.filter { device ->
        val matchesType = activeFilter == null || device.type == activeFilter
        val matchesSearch = searchQuery.isBlank() ||
                device.name.contains(searchQuery, ignoreCase = true) ||
                (device.ipAddress?.contains(searchQuery) == true) ||
                (device.macAddress?.contains(searchQuery, ignoreCase = true) == true)
        matchesType && matchesSearch
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search by name, IP, or MAC…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, null)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = activeFilter == null,
                    onClick = { viewModel.setDeviceFilter(null) },
                    label = { Text("All (${devices.size})") }
                )
            }
            items(DeviceType.entries) { type ->
                val count = devices.count { it.type == type }
                if (count > 0) {
                    FilterChip(
                        selected = activeFilter == type,
                        onClick = { viewModel.setDeviceFilter(if (activeFilter == type) null else type) },
                        label = { Text("${type.label} ($count)") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        DeviceColors[type.name] ?: Color.Gray
                                    )
                            )
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DeviceUnknown,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (devices.isEmpty()) "No devices found.\nRun a scan to discover devices."
                        else "No devices match your filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        expanded = expandedDeviceId == device.id,
                        onClick = {
                            expandedDeviceId = if (expandedDeviceId == device.id) null else device.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: Device, expanded: Boolean, onClick: () -> Unit) {
    val color = DeviceColors[device.type.name] ?: Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        deviceIcon(device.type),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        device.ipAddress?.let {
                            Chip(text = it, color = color)
                        }
                        Text(
                            device.type.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                device.signal?.let { rssi ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SignalIcon(rssi = rssi, color = color)
                        Text(
                            "${rssi}dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))

                    DeviceDetailRow("MAC", device.macAddress ?: "Unknown")
                    device.ipAddress?.let { DeviceDetailRow("IP", it) }
                    device.manufacturer?.let { DeviceDetailRow("Vendor", it) }
                    device.subnet?.let { DeviceDetailRow("Subnet", it) }
                    device.frequency?.let { DeviceDetailRow("Frequency", "${it} MHz") }
                    device.capabilities?.let { DeviceDetailRow("Details", it) }

                    if (device.services.isNotEmpty()) {
                        DeviceDetailRow("Services", device.services.joinToString(", "))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.65f)
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun SignalIcon(rssi: Int, color: Color) {
    val icon = when {
        rssi >= -50 -> Icons.Default.SignalWifi4Bar
        rssi >= -65 -> Icons.Default.NetworkWifi3Bar
        rssi >= -75 -> Icons.Default.NetworkWifi2Bar
        else -> Icons.Default.NetworkWifi1Bar
    }
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
}

fun deviceIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.WIFI_AP -> Icons.Default.Router
    DeviceType.WIFI_CLIENT -> Icons.Default.Wifi
    DeviceType.BT_CLASSIC -> Icons.Default.Bluetooth
    DeviceType.BT_LE -> Icons.Default.BluetoothSearching
    DeviceType.NETWORK_HOST -> Icons.Default.Computer
    DeviceType.MDNS_SERVICE -> Icons.Default.Hub
    DeviceType.ZIGBEE -> Icons.Default.Sensors
    DeviceType.THREAD -> Icons.Default.Hub
    DeviceType.UNKNOWN -> Icons.Default.DeviceUnknown
}
