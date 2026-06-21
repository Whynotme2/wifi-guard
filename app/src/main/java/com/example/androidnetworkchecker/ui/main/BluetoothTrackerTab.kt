package com.example.androidnetworkchecker.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

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
    var activeViewTab by rememberSaveable { mutableStateOf(0) } // 0 = List View, 1 = Map View
    var selectedCategory by rememberSaveable { mutableStateOf(0) } // 0 = All, 1 = Suspicious, 2 = Trusted

    // Request permissions launcher (Bluetooth + Location)
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
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
                Toast.makeText(context, "Location and Bluetooth permissions are required to scan BLE trackers.", Toast.LENGTH_LONG).show()
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
        // Top Tracker Heading Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BLE Anti-Stalker Tracker",
                    color = Slate50,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "GPS Map & log sync for BLE devices",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Export to GDrive/Wigle Button (CSV)
                IconButton(
                    onClick = { viewModel.exportToWigleCsv(context) },
                    modifier = Modifier
                        .background(Slate800, CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Wigle CSV",
                        tint = Teal500,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Export to Google Earth Button (KML)
                IconButton(
                    onClick = { viewModel.exportToKml(context) },
                    modifier = Modifier
                        .background(Slate800, CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Export Google Earth KML",
                        tint = Indigo500,
                        modifier = Modifier.size(18.dp)
                    )
                }

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
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                PermissionOnboardingCard(state = state, onRequestPermissions = { launcher.launch(permissionsToRequest) })
            }
        } else {
            // View Selector Tabs (List View vs Map View)
            TabRow(
                selectedTabIndex = activeViewTab,
                containerColor = Slate900,
                contentColor = Teal500,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(vertical = 4.dp)
            ) {
                Tab(
                    selected = activeViewTab == 0,
                    onClick = { activeViewTab = 0 },
                    text = { Text("List View", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (activeViewTab == 0) Teal500 else Slate400) }
                )
                Tab(
                    selected = activeViewTab == 1,
                    onClick = { activeViewTab = 1 },
                    text = { Text("Wigle GPS Map", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (activeViewTab == 1) Teal500 else Slate400) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeViewTab == 0) {
                // --- LIST VIEW ---
                // Stat overview badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val totalCount = state.devices.size
                    val suspCount = state.devices.count { it.isSuspicious }
                    val safeCount = state.devices.count { it.isSafe }

                    StatBadge(label = "Total Devices", count = totalCount, color = Slate400, modifier = Modifier.weight(1f))
                    StatBadge(label = "Suspicious", count = suspCount, color = if (suspCount > 0) Red500 else Slate400, modifier = Modifier.weight(1f))
                    StatBadge(label = "Trusted", count = safeCount, color = Emerald500, modifier = Modifier.weight(1f))
                }

                // Category filter row
                TabRow(
                    selectedTabIndex = selectedCategory,
                    containerColor = Slate900,
                    contentColor = Teal500,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(vertical = 2.dp)
                ) {
                    Tab(
                        selected = selectedCategory == 0,
                        onClick = { selectedCategory = 0 },
                        text = { Text("All", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (selectedCategory == 0) Teal500 else Slate400) }
                    )
                    Tab(
                        selected = selectedCategory == 1,
                        onClick = { selectedCategory = 1 },
                        text = { Text("Suspicious", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (selectedCategory == 1) Red500 else Slate400) }
                    )
                    Tab(
                        selected = selectedCategory == 2,
                        onClick = { selectedCategory = 2 },
                        text = { Text("Trusted", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (selectedCategory == 2) Emerald500 else Slate400) }
                    )
                }

                val filteredDevices = when (selectedCategory) {
                    1 -> state.devices.filter { it.isSuspicious }
                    2 -> state.devices.filter { it.isSafe }
                    else -> state.devices
                }
                val onlineDevices = filteredDevices.filter { it.isCurrentlyActive }
                val offlineDevices = filteredDevices.filter { !it.isCurrentlyActive }

                if (filteredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
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
                            .padding(horizontal = 16.dp)
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        if (onlineDevices.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Online Devices (${onlineDevices.size})",
                                    color = Teal500,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            items(onlineDevices, key = { it.address }) { device ->
                                DeviceCard(device = device, onToggleSafe = { viewModel.toggleSafeDevice(device.address) })
                            }
                        }
                        if (offlineDevices.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Offline Devices (${offlineDevices.size})",
                                    color = Slate400,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                                )
                            }
                            items(offlineDevices, key = { it.address }) { device ->
                                DeviceCard(device = device, onToggleSafe = { viewModel.toggleSafeDevice(device.address) })
                            }
                        }
                    }
                }
            } else {
                // --- WIGLE GPS MAP VIEW ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    MapViewContainer(
                        viewModel = viewModel,
                        devices = state.devices,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapViewContainer(
    viewModel: BluetoothTrackerViewModel,
    devices: List<BluetoothTrackerDevice>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val htmlContent = remember {
        try {
            val mapHtml = context.assets.open("map.html").bufferedReader().use { it.readText() }
            val css = context.assets.open("leaflet.css").bufferedReader().use { it.readText() }
            val js = context.assets.open("leaflet.js").bufferedReader().use { it.readText() }
            
            mapHtml
                .replace("<link rel=\"stylesheet\" href=\"leaflet.css\" />", "<style>$css</style>")
                .replace("<script src=\"leaflet.js\"></script>", "<script>$js</script>")
        } catch (e: Exception) {
            "<html><body><h3>Error loading map assets: ${e.localizedMessage}</h3></body></html>"
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true

                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun getDevicesJson(): String {
                        return viewModel.getDevicesJson()
                    }
                }, "AndroidInterface")

                loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        modifier = modifier,
        update = { webView ->
            webView.evaluateJavascript("if (typeof loadMarkers === 'function') { loadMarkers(); }", null)
        }
    )
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
                text = "Anti-Stalker GPS Map Setup",
                color = Slate50,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "To track BLE devices, plot them on the map, and compile Wigle logs, this utility requires location permissions. We pin device markers at the point of strongest signal strength.",
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = device.manufacturerName,
                        color = Slate50,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (device.isCurrentlyActive) {
                        Box(
                            modifier = Modifier
                                .background(Teal500.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, Teal500, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("ONLINE", color = Teal500, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Slate700.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, Slate700, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("OFFLINE", color = Slate400, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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
                    text = "RSSI: ${device.rssi} dBm • Seen ${device.scanCount} times",
                    color = Slate400,
                    fontSize = 10.sp
                )
                val timeFormatter = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US) }
                val firstSeenStr = remember(device.firstSeen) { timeFormatter.format(java.util.Date(device.firstSeen)) }
                val lastSeenStr = remember(device.lastSeen) { timeFormatter.format(java.util.Date(device.lastSeen)) }
                Text(
                    text = "First seen: $firstSeenStr  •  Last seen: $lastSeenStr",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                if (device.latitude != null && device.longitude != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "GPS Coordinates",
                            tint = Teal500,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "GPS: %.4f, %.4f", device.latitude, device.longitude),
                            color = Teal500,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
