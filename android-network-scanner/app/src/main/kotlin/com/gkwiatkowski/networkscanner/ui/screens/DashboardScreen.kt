package com.gkwiatkowski.networkscanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gkwiatkowski.networkscanner.data.model.DeviceType
import com.gkwiatkowski.networkscanner.scanner.ScanStatus
import com.gkwiatkowski.networkscanner.ui.MainViewModel
import com.gkwiatkowski.networkscanner.ui.theme.DeviceColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val devices by viewModel.latestDevices.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val bgEnabled by viewModel.backgroundScanEnabled.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()

    val wifiCount = devices.count { it.type == DeviceType.WIFI_AP || it.type == DeviceType.WIFI_CLIENT }
    val btCount = devices.count { it.type == DeviceType.BT_CLASSIC || it.type == DeviceType.BT_LE }
    val netCount = devices.count { it.type == DeviceType.NETWORK_HOST || it.type == DeviceType.MDNS_SERVICE }
    val lastSession = sessions.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScanRadarCard(
            isScanning = isScanning,
            scanStatus = scanStatus,
            totalDevices = devices.size,
            onScanClick = { viewModel.startManualScan() }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Wifi,
                label = "Wi-Fi",
                value = wifiCount.toString(),
                color = Color(DeviceColors["WIFI_AP"]!!.value)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Bluetooth,
                label = "Bluetooth",
                value = btCount.toString(),
                color = Color(DeviceColors["BT_CLASSIC"]!!.value)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Hub,
                label = "Network",
                value = netCount.toString(),
                color = Color(DeviceColors["NETWORK_HOST"]!!.value)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Background Scanning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (bgEnabled) "Active — every 10 min" else "Disabled",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (lastSession != null) {
                            Text(
                                "Last scan: ${formatTime(lastSession.startTime)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = bgEnabled,
                        onCheckedChange = { viewModel.toggleBackgroundScanning(it) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = scanStatus is ScanStatus.Scanning,
            enter = fadeIn() + expandVertically()
        ) {
            val scanning = scanStatus as? ScanStatus.Scanning
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        scanning?.phase ?: "Scanning…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if ((scanning?.progress ?: 0) > 0) {
                        LinearProgressIndicator(
                            progress = { (scanning?.progress ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        if (sessions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Recent Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    sessions.take(5).forEach { session ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(session.startTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${session.deviceCount} devices",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (sessions.indexOf(session) < sessions.take(5).size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanRadarCard(
    isScanning: Boolean,
    scanStatus: ScanStatus,
    totalDevices: Int,
    onScanClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                    )
                }
                FloatingActionButton(
                    onClick = onScanClick,
                    modifier = Modifier.size(72.dp),
                    containerColor = if (isScanning)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                        contentDescription = "Scan",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Text(
                if (isScanning) "Scanning…" else "Tap to scan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (totalDevices > 0) {
                Text(
                    "$totalDevices devices found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (scanStatus is ScanStatus.Complete) {
                Text(
                    "Last scan: ${scanStatus.deviceCount} devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
