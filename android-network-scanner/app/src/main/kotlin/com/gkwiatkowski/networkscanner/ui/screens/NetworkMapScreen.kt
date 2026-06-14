package com.gkwiatkowski.networkscanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gkwiatkowski.networkscanner.data.model.Device
import com.gkwiatkowski.networkscanner.data.model.DeviceType
import com.gkwiatkowski.networkscanner.ui.MainViewModel
import com.gkwiatkowski.networkscanner.ui.theme.DeviceColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun NetworkMapScreen(viewModel: MainViewModel) {
    val devices by viewModel.latestDevices.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val networkDevices = devices.filter { it.ipAddress != null }
    val subnetGroups = viewModel.getDevicesBySubnet(networkDevices)

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Topology") },
                icon = { Icon(Icons.Default.AccountTree, null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Subnets") },
                icon = { Icon(Icons.Default.Hub, null) }
            )
        }

        when (selectedTab) {
            0 -> TopologyView(devices = devices, subnetGroups = subnetGroups)
            1 -> SubnetListView(subnetGroups = subnetGroups)
        }
    }
}

@Composable
private fun TopologyView(
    devices: List<Device>,
    subnetGroups: Map<String, List<Device>>
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    if (devices.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No devices to map.\nRun a scan first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        return
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.3f, 3f)
                    offset += pan
                }
            }
    ) {
        translate(offset.x, offset.y) {
            scale(scale, pivot = center) {
                drawNetworkTopology(
                    drawScope = this,
                    devices = devices,
                    subnetGroups = subnetGroups,
                    textMeasurer = textMeasurer,
                    primaryColor = primaryColor,
                    onSurface = onSurface,
                    surfaceVariant = surfaceVariant
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(onClick = { scale = (scale * 1.2f).coerceIn(0.3f, 3f) }) {
                Icon(Icons.Default.ZoomIn, null)
            }
            SmallFloatingActionButton(onClick = { scale = (scale / 1.2f).coerceIn(0.3f, 3f) }) {
                Icon(Icons.Default.ZoomOut, null)
            }
            SmallFloatingActionButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                Icon(Icons.Default.CenterFocusStrong, null)
            }
        }
    }
}

private fun DrawScope.drawNetworkTopology(
    drawScope: DrawScope,
    devices: List<Device>,
    subnetGroups: Map<String, List<Device>>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    primaryColor: Color,
    onSurface: Color,
    surfaceVariant: Color
) {
    val cx = size.width / 2
    val cy = size.height / 2
    val phoneRadius = 28f
    val nodeRadius = 18f

    drawCircle(color = primaryColor.copy(alpha = 0.2f), radius = phoneRadius + 8f, center = Offset(cx, cy))
    drawCircle(color = primaryColor, radius = phoneRadius, center = Offset(cx, cy))

    val textResult = textMeasurer.measure(
        text = "Phone",
        style = TextStyle(color = onSurface, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    )
    drawText(textResult, topLeft = Offset(cx - textResult.size.width / 2f, cy + phoneRadius + 4f))

    if (subnetGroups.isEmpty()) {
        val angleStep = if (devices.size > 0) (2 * Math.PI / devices.size).toFloat() else 0f
        val orbitRadius = min(size.width, size.height) * 0.35f

        devices.forEachIndexed { i, device ->
            val angle = i * angleStep - Math.PI.toFloat() / 2
            val nx = cx + orbitRadius * cos(angle)
            val ny = cy + orbitRadius * sin(angle)

            drawLine(
                color = onSurface.copy(alpha = 0.2f),
                start = Offset(cx, cy),
                end = Offset(nx, ny),
                strokeWidth = 1.5f
            )

            val deviceColor = DeviceColors[device.type.name] ?: Color.Gray
            drawCircle(color = deviceColor.copy(alpha = 0.2f), radius = nodeRadius + 4f, center = Offset(nx, ny))
            drawCircle(color = deviceColor, radius = nodeRadius, center = Offset(nx, ny))

            val label = (device.ipAddress ?: device.name).take(12)
            val labelResult = textMeasurer.measure(
                text = label,
                style = TextStyle(color = onSurface, fontSize = 8.sp)
            )
            drawText(labelResult, topLeft = Offset(nx - labelResult.size.width / 2f, ny + nodeRadius + 2f))
        }
        return
    }

    val subnetList = subnetGroups.keys.toList()
    val subnetAngleStep = if (subnetList.size > 0) (2 * Math.PI / subnetList.size).toFloat() else 0f
    val subnetOrbit = min(size.width, size.height) * 0.28f
    val deviceOrbit = 80f

    subnetList.forEachIndexed { si, subnet ->
        val subnetAngle = si * subnetAngleStep - Math.PI.toFloat() / 2
        val sx = cx + subnetOrbit * cos(subnetAngle)
        val sy = cy + subnetOrbit * sin(subnetAngle)

        drawLine(
            color = primaryColor.copy(alpha = 0.5f),
            start = Offset(cx, cy),
            end = Offset(sx, sy),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )

        drawCircle(color = surfaceVariant, radius = 24f, center = Offset(sx, sy))
        drawCircle(
            color = primaryColor,
            radius = 24f,
            center = Offset(sx, sy),
            style = Stroke(width = 2f)
        )

        val subnetLabel = subnet.split(".").take(3).joinToString(".") + ".x"
        val subLabelResult = textMeasurer.measure(
            text = subnetLabel,
            style = TextStyle(color = onSurface, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        )
        drawText(subLabelResult, topLeft = Offset(sx - subLabelResult.size.width / 2f, sy - subLabelResult.size.height / 2f))

        val deviceList = subnetGroups[subnet] ?: emptyList()
        val deviceAngleStep = if (deviceList.size > 0) (2 * Math.PI / deviceList.size).toFloat() else 0f

        deviceList.forEachIndexed { di, device ->
            val deviceAngle = di * deviceAngleStep + subnetAngle
            val dx = sx + deviceOrbit * cos(deviceAngle)
            val dy = sy + deviceOrbit * sin(deviceAngle)

            drawLine(
                color = onSurface.copy(alpha = 0.15f),
                start = Offset(sx, sy),
                end = Offset(dx, dy),
                strokeWidth = 1f
            )

            val deviceColor = DeviceColors[device.type.name] ?: Color.Gray
            drawCircle(color = deviceColor, radius = nodeRadius, center = Offset(dx, dy))

            val ipLabel = device.ipAddress?.split(".")?.lastOrNull() ?: device.name.take(8)
            val ipResult = textMeasurer.measure(
                text = ipLabel,
                style = TextStyle(color = onSurface, fontSize = 7.sp)
            )
            drawText(ipResult, topLeft = Offset(dx - ipResult.size.width / 2f, dy + nodeRadius + 2f))
        }
    }
}

@Composable
private fun SubnetListView(subnetGroups: Map<String, List<Device>>) {
    if (subnetGroups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No network devices found.\nRun a scan to discover devices.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(subnetGroups.keys.toList()) { subnet ->
            val subnetDevices = subnetGroups[subnet] ?: emptyList()
            SubnetCard(subnet = subnet, devices = subnetDevices)
        }
    }
}

@Composable
private fun SubnetCard(subnet: String, devices: List<Device>) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(bottom = if (expanded) 8.dp else 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(subnet, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${devices.size} hosts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))

                    val usedIps = devices.mapNotNull { it.ipAddress }.toSet()
                    val subnetBase = subnet.substringBeforeLast(".")
                    val usageText = "${usedIps.size}/254 addresses used"

                    LinearProgressIndicator(
                        progress = { usedIps.size / 254f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        usageText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    devices.sortedBy { it.ipAddress }.forEach { device ->
                        SubnetDeviceRow(device = device)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubnetDeviceRow(device: Device) {
    val color = DeviceColors[device.type.name] ?: Color.Gray
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
        Text(
            device.ipAddress ?: "—",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(100.dp)
        )
        Text(
            device.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            device.type.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

