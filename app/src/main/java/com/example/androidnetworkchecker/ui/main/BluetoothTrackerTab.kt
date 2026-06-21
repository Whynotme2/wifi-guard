package com.example.androidnetworkchecker.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin

// Theme Colors
private val Slate900 = Color(0xFF0F172A)
private val Slate800 = Color(0xFF1E293B)
private val Slate700 = Color(0xFF334155)
private val Slate400 = Color(0xFF94A3B8)
private val Slate50 = Color(0xFFF8FAFC)
private val Teal500 = Color(0xFF14B8A6)
private val Indigo500 = Color(0xFF6366F1)
private val Emerald500 = Color(0xFF10B981)
private val Red500 = Color(0xFFEF4444)
private val Amber500 = Color(0xFFF59E0B)

@Composable
fun BluetoothTrackerTab(viewModel: BluetoothTrackerViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedCategory by rememberSaveable { mutableStateOf(0) } // 0 = All, 1 = Suspicious, 2 = Trusted

    // Request permissions launcher
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allGranted = permissions.values.all { it }
            viewModel.checkStatus()
            if (allGranted) {
                viewModel.startScanning()
            } else {
                Toast.makeText(context, "Bluetooth scan permissions are required to detect tracker tags.", Toast.LENGTH_LONG).show()
            }
        }
    )

    // Automatically check status and start scanning on entry
    DisposableEffect(Unit) {
        viewModel.checkStatus()
        if (state.hasPermissions && state.isBluetoothEnabled) {
            viewModel.startScanning()
        }
        onDispose {
            viewModel.stopScanning()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        // Top Tracker Heading
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BLE Anti-Stalker Tracker",
                    color = Slate50,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Detects hidden AirTags & beacon tracking accessories",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { viewModel.clearDeviceHistory() },
                    modifier = Modifier
                        .background(Slate800, CircleShape)
                        .size(36.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = Slate400, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = {
                        if (!state.hasPermissions) {
                            launcher.launch(permissionsToRequest)
                        } else {
                            if (state.isScanning) viewModel.stopScanning() else viewModel.startScanning()
                        }
                    },
                    modifier = Modifier
                        .background(if (state.isScanning) Teal500.copy(alpha = 0.2f) else Slate800, CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan Toggle",
                        tint = if (state.isScanning) Teal500 else Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (!state.hasPermissions || !state.isBluetoothEnabled) {
            PermissionOnboardingCard(state = state, onRequestPermissions = { launcher.launch(permissionsToRequest) })
        } else {
            // Radar sweeping visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                RadarVisualizer(devices = state.devices, isScanning = state.isScanning)
            }

            // Stat overview badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val totalCount = state.devices.size
                val suspCount = state.devices.count { it.isSuspicious }
                val safeCount = state.devices.count { it.isSafe }

                StatBadge(label = "Total Devices", count = totalCount, color = Slate400, modifier = Modifier.weight(1f))
                StatBadge(label = "Suspicious", count = suspCount, color = if (suspCount > 0) Red500 else Slate400, modifier = Modifier.weight(1f))
                StatBadge(label = "Trusted", count = safeCount, color = Emerald500, modifier = Modifier.weight(1f))
            }

            // Tabs to filter by categories
            TabRow(
                selectedTabIndex = selectedCategory,
                containerColor = Slate900,
                contentColor = Teal500,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Tab(
                    selected = selectedCategory == 0,
                    onClick = { selectedCategory = 0 },
                    text = { Text("All", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedCategory == 0) Teal500 else Slate400) }
                )
                Tab(
                    selected = selectedCategory == 1,
                    onClick = { selectedCategory = 1 },
                    text = { Text("Suspicious", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedCategory == 1) Red500 else Slate400) }
                )
                Tab(
                    selected = selectedCategory == 2,
                    onClick = { selectedCategory = 2 },
                    text = { Text("Trusted", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selectedCategory == 2) Emerald500 else Slate400) }
                )
            }

            // Device list
            val filteredDevices = when (selectedCategory) {
                1 -> state.devices.filter { it.isSuspicious }
                2 -> state.devices.filter { it.isSafe }
                else -> state.devices
            }

            if (filteredDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Slate700, modifier = Modifier.size(48.dp))
                        Text(
                            text = if (state.isScanning) "Searching for trackers..." else "Scan inactive. Tap refresh to start.",
                            color = Slate400,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    items(filteredDevices, key = { it.address }) { device ->
                        DeviceCard(device = device, onToggleSafe = { viewModel.toggleSafeDevice(device.address) })
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionOnboardingCard(state: BluetoothTrackerState, onRequestPermissions: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .border(1.dp, Slate700, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Teal500.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Teal500,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "Anti-Stalker Tracker Setup",
                color = Slate50,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "To search for commercial BLE trackers (such as Apple AirTags or Tiles) moving near you, the system requires Bluetooth scanning and coarse location authorization. No location data is collected or shared.",
                color = Slate400,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Authorize & Enable Scanner", fontWeight = FontWeight.Bold)
            }

            if (state.errorMessage != null && state.hasPermissions) {
                Text(
                    text = state.errorMessage,
                    color = Red500,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun StatBadge(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = modifier.border(1.dp, Slate700, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text(text = count.toString(), color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun RadarVisualizer(devices: List<BluetoothTrackerDevice>, isScanning: Boolean) {
    // 0 to 360 degree sweeping angle transition
    val sweepTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val wavePulseTransition = rememberInfiniteTransition(label = "WavePulse")
    val waveScale by wavePulseTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveScale"
    )

    Canvas(modifier = Modifier.size(190.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        // Draw background radar scope circles
        drawCircle(
            color = Slate800,
            radius = radius
        )
        drawCircle(
            color = Slate700.copy(alpha = 0.5f),
            radius = radius,
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(
            color = Slate700.copy(alpha = 0.3f),
            radius = radius * 0.66f,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = Slate700.copy(alpha = 0.2f),
            radius = radius * 0.33f,
            style = Stroke(width = 1.dp.toPx())
        )

        // Draw crosshairs
        drawLine(
            color = Slate700.copy(alpha = 0.5f),
            start = Offset(center.x, 0f),
            end = Offset(center.x, size.height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Slate700.copy(alpha = 0.5f),
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 1.dp.toPx()
        )

        // Draw pulse scan waves if scanning
        if (isScanning) {
            drawCircle(
                color = Teal500.copy(alpha = 0.15f * (1.0f - waveScale)),
                radius = radius * waveScale
            )

            // Sweep visualizer arc
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.8f to Teal500.copy(alpha = 0.05f),
                    1f to Teal500.copy(alpha = 0.35f),
                    center = center
                ),
                startAngle = sweepAngle - 30f,
                sweepAngle = 30f,
                useCenter = true
            )
        }

        // Draw user/device anchor in center
        drawCircle(
            color = Teal500,
            radius = 6.dp.toPx()
        )
        drawCircle(
            color = Teal500.copy(alpha = 0.2f),
            radius = 12.dp.toPx()
        )

        // Plot tracked BLE devices onto the radar
        devices.forEach { device ->
            // Compute a stable angle for each MAC address
            val deviceAngle = (device.address.hashCode() % 360).toFloat()
            val angleRad = Math.toRadians(deviceAngle.toDouble())

            // Compute radial fraction (0.1 to 0.95) based on estimated distance (max 20m)
            val distanceFraction = (device.estimatedDistanceMeters / 15.0).coerceIn(0.1, 0.95)
            val deviceRadius = (radius * distanceFraction).toFloat()

            val x = center.x + deviceRadius * cos(angleRad).toFloat()
            val y = center.y + deviceRadius * sin(angleRad).toFloat()

            // Calculate sweep proximity to trigger brightness pulse
            val angleDiff = Math.abs((sweepAngle - deviceAngle + 180) % 360 - 180)
            val brightnessFactor = if (isScanning && angleDiff < 30) {
                1.0f - (angleDiff / 30f) * 0.7f
            } else {
                0.3f
            }

            val dotColor = when {
                device.isSafe -> Emerald500
                device.isSuspicious -> Red500
                else -> Indigo500
            }

            // Draw glowing radar blips
            drawCircle(
                color = dotColor.copy(alpha = brightnessFactor * 0.3f),
                radius = 10.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = dotColor.copy(alpha = brightnessFactor),
                radius = 5.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun DeviceCard(device: BluetoothTrackerDevice, onToggleSafe: () -> Unit) {
    val cardBorderColor = when {
        device.isSafe -> Emerald500.copy(alpha = 0.3f)
        device.isSuspicious -> Red500.copy(alpha = 0.5f)
        else -> Slate700
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Status Icon Indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = when {
                            device.isSafe -> Emerald500.copy(alpha = 0.1f)
                            device.isSuspicious -> Red500.copy(alpha = 0.1f)
                            else -> Slate700.copy(alpha = 0.3f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        device.isSafe -> Icons.Default.CheckCircle
                        device.isSuspicious -> Icons.Default.Warning
                        else -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = when {
                        device.isSafe -> Emerald500
                        device.isSuspicious -> Red500
                        else -> Slate400
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            // Device details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = device.manufacturerName,
                        color = Slate50,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (device.isSuspicious) {
                        Box(
                            modifier = Modifier
                                .background(Red500, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("STALKER ALERT", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Text(
                    text = device.name ?: device.address,
                    color = Slate400,
                    fontSize = 11.sp
                )
                Text(
                    text = "Signals: ${device.rssi} dBm • Seen ${device.scanCount} times",
                    color = Slate400,
                    fontSize = 10.sp
                )
            }

            // Right side distance indicator and trust action button
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "~${device.estimatedDistanceMeters}m",
                    color = when {
                        device.isSuspicious -> Red500
                        device.estimatedDistanceMeters < 3.0 -> Amber500
                        else -> Teal500
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )

                // Trust/Untrust Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (device.isSafe) Emerald500.copy(alpha = 0.2f) else Slate700)
                        .clickable { onToggleSafe() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (device.isSafe) "Untrust" else "Trust",
                        color = if (device.isSafe) Emerald500 else Slate400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}
