package com.gkwiatkowski.networkscanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gkwiatkowski.networkscanner.data.model.ScanSession
import com.gkwiatkowski.networkscanner.data.model.TriggerSource
import com.gkwiatkowski.networkscanner.ui.MainViewModel
import com.gkwiatkowski.networkscanner.ui.theme.DeviceColors
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val sessions by viewModel.allSessions.collectAsState()
    val selectedDevices by viewModel.selectedSessionDevices.collectAsState()
    var expandedSessionId by remember { mutableLongStateOf(-1L) }

    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    "No scan history yet.\nRun your first scan from the Dashboard.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HistorySummaryCard(sessions = sessions)
        }

        items(sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                expanded = expandedSessionId == session.id,
                sessionDevices = if (expandedSessionId == session.id) selectedDevices else emptyList(),
                onClick = {
                    if (expandedSessionId == session.id) {
                        expandedSessionId = -1L
                    } else {
                        expandedSessionId = session.id
                        viewModel.loadSessionDevices(session.id)
                    }
                }
            )
        }
    }
}

@Composable
private fun HistorySummaryCard(sessions: List<ScanSession>) {
    val totalScans = sessions.size
    val totalDevices = sessions.sumOf { it.deviceCount }
    val avgDevices = if (totalScans > 0) totalDevices / totalScans else 0
    val maxDevices = sessions.maxOfOrNull { it.deviceCount } ?: 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Scan History Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                HistoryStatItem(value = "$totalScans", label = "Total Scans")
                HistoryStatItem(value = "$avgDevices", label = "Avg Devices")
                HistoryStatItem(value = "$maxDevices", label = "Max Found")
            }
        }
    }
}

@Composable
private fun HistoryStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun SessionCard(
    session: ScanSession,
    expanded: Boolean,
    sessionDevices: List<com.gkwiatkowski.networkscanner.data.model.Device>,
    onClick: () -> Unit
) {
    val duration = session.endTime?.let { it - session.startTime }
    val durationStr = duration?.let { formatDuration(it) } ?: "In progress"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        formatDateTime(session.startTime),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (session.triggeredBy) {
                                TriggerSource.MANUAL -> Icons.Default.TouchApp
                                TriggerSource.SCHEDULED -> Icons.Default.Schedule
                                TriggerSource.BOOT -> Icons.Default.PowerSettingsNew
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${session.triggeredBy.name.lowercase().replaceFirstChar { it.uppercase() }} · $durationStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${session.deviceCount}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "devices",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (session.wifiCount > 0) {
                    CountChip(
                        count = session.wifiCount,
                        label = "Wi-Fi",
                        color = DeviceColors["WIFI_AP"] ?: Color.Blue
                    )
                }
                if (session.bluetoothCount > 0) {
                    CountChip(
                        count = session.bluetoothCount,
                        label = "BT",
                        color = DeviceColors["BT_CLASSIC"] ?: Color.Blue
                    )
                }
                if (session.networkCount > 0) {
                    CountChip(
                        count = session.networkCount,
                        label = "Network",
                        color = DeviceColors["NETWORK_HOST"] ?: Color.Green
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    if (sessionDevices.isEmpty()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        sessionDevices.take(10).forEach { device ->
                            DeviceCard(
                                device = device,
                                expanded = false,
                                onClick = {}
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (sessionDevices.size > 10) {
                            Text(
                                "+ ${sessionDevices.size - 10} more devices",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountChip(count: Int, label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            "$count $label",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatDateTime(millis: Long): String {
    val sdf = SimpleDateFormat("EEE, MMM dd · HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

private fun Modifier.clickable(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
