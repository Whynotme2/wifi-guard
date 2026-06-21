package com.example.androidnetworkchecker.ui.main

import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.androidnetworkchecker.data.*

// Premium Color System
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
private val Purple500 = Color(0xFF8B5CF6)

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spyViewModel: SpyScannerViewModel = viewModel { SpyScannerViewModel(context) }
    val bluetoothTrackerViewModel: BluetoothTrackerViewModel = viewModel { BluetoothTrackerViewModel(context) }

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var activePortScanIp by rememberSaveable { mutableStateOf<String?>(null) }
    var showPortScanDialog by rememberSaveable { mutableStateOf(false) }
    
    var showWolDialog by rememberSaveable { mutableStateOf(false) }
    var wolTargetIp by rememberSaveable { mutableStateOf("") }

    // Automatically load network details on startup
    LaunchedEffect(Unit) {
        viewModel.refreshNetworkState(context)
    }

    // Monitor Wi-Fi signal details when on Dashboard tab (Tab 0)
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            viewModel.startWifiSignalMonitoring(context)
        } else {
            viewModel.stopWifiSignalMonitoring()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Slate800,
                tonalElevation = 8.dp,
                modifier = Modifier.border(1.dp, Slate700, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Teal500,
                        selectedTextColor = Teal500,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400,
                        indicatorColor = Slate700
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Scanner") },
                    label = { Text("Scanner", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Teal500,
                        selectedTextColor = Teal500,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400,
                        indicatorColor = Slate700
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Diagnostics") },
                    label = { Text("Diagnostics", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Teal500,
                        selectedTextColor = Teal500,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400,
                        indicatorColor = Slate700
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Tools") },
                    label = { Text("Tools", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Teal500,
                        selectedTextColor = Teal500,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400,
                        indicatorColor = Slate700
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Guard Settings") },
                    label = { Text("Guard", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Teal500,
                        selectedTextColor = Teal500,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400,
                        indicatorColor = Slate700
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    icon = { Icon(Icons.Default.Visibility, contentDescription = "Physical Detector") },
                    label = { Text("Detector", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Teal500,
                        selectedTextColor = Teal500,
                        unselectedIconColor = Slate400,
                        unselectedTextColor = Slate400,
                        indicatorColor = Slate700
                    )
                )
            }
        },
        containerColor = Slate900
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardTab(state = state, viewModel = viewModel, context = context)
                1 -> ScannerTab(
                    state = state, 
                    viewModel = viewModel, 
                    context = context, 
                    onScanHostPorts = { ip ->
                        activePortScanIp = ip
                        showPortScanDialog = true
                        viewModel.resetPortScan()
                    },
                    onTriggerWol = { ip ->
                        wolTargetIp = ip
                        showWolDialog = true
                    }
                )
                2 -> DiagnosticsTab(state = state, viewModel = viewModel)
                3 -> ToolsTab(state = state, viewModel = viewModel, context = context)
                4 -> GuardTab(state = state, viewModel = viewModel)
                5 -> SpyDetectorTab(viewModel = spyViewModel, bluetoothViewModel = bluetoothTrackerViewModel)
            }

            // Advanced Port Scanner Dialog Overlay
            if (showPortScanDialog && activePortScanIp != null) {
                PortScannerDialog(
                    ip = activePortScanIp!!,
                    state = state,
                    viewModel = viewModel,
                    onDismiss = {
                        viewModel.resetPortScan()
                        showPortScanDialog = false
                    }
                )
            }
            
            // Wake-on-LAN Trigger Dialog Overlay
            if (showWolDialog && wolTargetIp.isNotEmpty()) {
                var macInput by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showWolDialog = false },
                    title = { Text("Send Wake-on-LAN (WoL)", color = Slate50, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Specify the MAC address for target host $wolTargetIp to broadcast the UDP Magic Packet.", color = Slate400, fontSize = 12.sp)
                            OutlinedTextField(
                                value = macInput,
                                onValueChange = { macInput = it },
                                label = { Text("MAC Address (AA:BB:CC:DD:EE:FF)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Teal500,
                                    unfocusedBorderColor = Slate700,
                                    focusedLabelColor = Teal500,
                                    unfocusedLabelColor = Slate400,
                                    focusedTextColor = Slate50,
                                    unfocusedTextColor = Slate50
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val cleanMac = macInput.replace(":", "").replace("-", "").trim()
                                if (cleanMac.length == 12) {
                                    viewModel.sendWakeOnLan(macInput)
                                    Toast.makeText(context, "Magic Packet broadcasted for $macInput", Toast.LENGTH_SHORT).show()
                                    showWolDialog = false
                                } else {
                                    Toast.makeText(context, "Please enter a valid 12-digit MAC Address", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal500)
                        ) {
                            Text("Send Packet", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWolDialog = false }) {
                            Text("Cancel", color = Slate400)
                        }
                    },
                    containerColor = Slate800,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

// ==========================================
// TABS IMPLEMENTATION
// ==========================================

@Composable
fun DashboardTab(
    state: NetworkState,
    viewModel: MainScreenViewModel,
    context: android.content.Context
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            HeaderSection(
                onRefresh = { viewModel.refreshNetworkState(context) },
                isRefreshing = state.isTestingDns || state.scanState.status == ScanStatus.SCANNING
            )
        }



        item {
            DashboardQuickIpsCard(
                state = state,
                onScanTrigger = { _, _, _, _ ->
                    Toast.makeText(context, "Please use the Scanner tab to scan local networks", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            ActiveConnectionCard(
                state = state,
                onScanClick = {
                    Toast.makeText(context, "Please use the Scanner tab to configure and run scans", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            DnsLeakTestCard(
                state = state,
                onRunTest = { viewModel.runDnsLeakTest() },
                onClearTest = { viewModel.clearDnsLeakTest() }
            )
        }

        item {
            Text(
                text = "Network Interfaces Details",
                color = Slate50,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
        }

        val categorizedInterfaces = state.interfaces.groupBy { it.type }
        InterfaceType.values().forEach { interfaceType ->
            val list = categorizedInterfaces[interfaceType] ?: emptyList()
            if (list.isNotEmpty()) {
                item {
                    ExpandableInterfaceCategory(
                        type = interfaceType,
                        interfaces = list,
                        onScanClick = { _ ->
                            Toast.makeText(context, "Please scan subnets inside the Scanner tab", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        item {
            CoffeeDonationCard()
        }
    }
}

@Composable
fun ScannerTab(
    state: NetworkState,
    viewModel: MainScreenViewModel,
    context: android.content.Context,
    onScanHostPorts: (String) -> Unit,
    onTriggerWol: (String) -> Unit
) {
    val scanState = state.scanState
    var selectedIfaceIndex by remember { mutableStateOf(0) }
    val eligibleInterfaces = state.interfaces.filter { 
        it.type == InterfaceType.WIFI || it.type == InterfaceType.MOBILE || it.type == InterfaceType.VPN 
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Subnet Host Scanner",
                color = Slate50,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
            Text(
                text = "Discover active devices on local subnets with ping verification",
                color = Slate400,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (eligibleInterfaces.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No active networks (Wi-Fi, Cellular, or VPN) found to scan.", color = Red500, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Select Target Interface", color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Slate900.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            eligibleInterfaces.forEachIndexed { idx, iface ->
                                val selected = idx == selectedIfaceIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) Teal500 else Color.Transparent)
                                        .clickable { if (scanState.status != ScanStatus.SCANNING) selectedIfaceIndex = idx }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${iface.type.name}: ${iface.name}",
                                        color = if (selected) Slate900 else Slate50,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        val activeIface = eligibleInterfaces.getOrNull(selectedIfaceIndex)
                        if (activeIface != null) {
                            val ip = activeIface.ipv4Addresses.firstOrNull() ?: activeIface.ipv6Addresses.firstOrNull() ?: "127.0.0.1"
                            DetailRow(label = "Local IP Address", value = ip)
                            DetailRow(label = "Subnet Range", value = "$ip/${activeIface.prefixLength}")
                            
                            if (activeIface.type == InterfaceType.MOBILE) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Amber500.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .border(1.dp, Amber500, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "⚠️ Cellular subnets are highly filtered by carrier operators. Scans may return empty results.",
                                        color = Amber500,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (scanState.status == ScanStatus.IDLE) {
                                Button(
                                    onClick = { viewModel.startSubnetScan(context, activeIface.name, ip, activeIface.prefixLength) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Scan Subnet Range", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            
            if (scanState.status == ScanStatus.IDLE) {
                item {
                    WakeOnLanCard(viewModel = viewModel)
                }
            }
        }

        if (scanState.status == ScanStatus.SCANNING) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Teal500)
                        
                        val percentage = if (scanState.totalCount > 0) {
                            (scanState.scannedCount * 100) / scanState.totalCount
                        } else 0
                        
                        Text(
                            text = "Scanning Subnet: $percentage% complete",
                            color = Slate50,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        
                        Text(
                            text = "Checked ${scanState.scannedCount} of ${scanState.totalCount} hosts\nFound ${scanState.activeHosts.size} active devices",
                            color = Slate400,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { viewModel.cancelSubnetScan() },
                            colors = ButtonDefaults.buttonColors(containerColor = Red500),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel Scan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (scanState.status == ScanStatus.FINISHED || scanState.status == ScanStatus.ERROR) {
            item {
                Text(
                    text = "Discovered Hosts (${scanState.activeHosts.size})",
                    color = Slate50,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (scanState.activeHosts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No active devices found. Try refreshing configuration or disabling VPN.", color = Slate400, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(scanState.activeHosts) { host ->
                    val clipboard = LocalClipboardManager.current
                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        clipboard.setText(AnnotatedString(host.ip))
                                        Toast.makeText(context, "Copied: ${host.ip}", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Text(
                                    text = host.ip,
                                    color = Slate50,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = host.hostname ?: "Device (No hostname)",
                                    color = Slate400,
                                    fontSize = 12.sp
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Wake-on-LAN Trigger Button next to host
                                IconButton(
                                    onClick = { onTriggerWol(host.ip) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Teal500.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Trigger WoL Packet",
                                        tint = Teal500,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                Button(
                                    onClick = { onScanHostPorts(host.ip) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Scan Ports",
                                        tint = Slate50,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ports", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resetSubnetScan() },
                        border = BorderStroke(1.dp, Slate700),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Scanner", fontWeight = FontWeight.Bold)
                    }
                    val activeIface = eligibleInterfaces.getOrNull(selectedIfaceIndex)
                    if (activeIface != null) {
                        val ip = activeIface.ipv4Addresses.firstOrNull() ?: activeIface.ipv6Addresses.firstOrNull() ?: "127.0.0.1"
                        Button(
                            onClick = { viewModel.startSubnetScan(context, activeIface.name, ip, activeIface.prefixLength) },
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Scan Again", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            item {
                WakeOnLanCard(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PingJitterTab(
    state: NetworkState,
    viewModel: MainScreenViewModel
) {
    val pingState = state.pingJitterState
    var hostInput by remember { mutableStateOf("8.8.8.8") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Ping Jitter & Quality",
                color = Slate50,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
            Text(
                text = "Continuous latency tracker for testing network stability",
                color = Slate400,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Quality Diagnostics Target", color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { if (pingState.status != PingJitterStatus.RUNNING) hostInput = it },
                        label = { Text("IP or Hostname") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate50,
                            focusedBorderColor = Teal500,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = Teal500
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pingState.status == PingJitterStatus.RUNNING) {
                            Button(
                                onClick = { viewModel.cancelPingJitter() },
                                colors = ButtonDefaults.buttonColors(containerColor = Red500),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Stop Engine", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.resetPingJitter() },
                                border = BorderStroke(1.dp, Slate700),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset data", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.startPingJitter(hostInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Start diagnostics", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (pingState.status == PingJitterStatus.RUNNING || pingState.latencies.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Engine Metrics", color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (pingState.status == PingJitterStatus.RUNNING) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Emerald500))
                                    Text("Running LIVE", color = Emerald500, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricBox(label = "Avg Latency", value = "%.1f ms".format(pingState.avgMs), color = Teal500, modifier = Modifier.weight(1f))
                            MetricBox(label = "Jitter (Var)", value = "%.1f ms".format(pingState.jitterMs), color = Indigo500, modifier = Modifier.weight(1f))
                            MetricBox(label = "Packet Loss", value = "%.1f%%".format(pingState.packetLossPercent), color = if (pingState.packetLossPercent > 0) Red500 else Emerald500, modifier = Modifier.weight(1f))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricBox(label = "Minimum RTT", value = "%.1f ms".format(pingState.minMs), color = Slate400, modifier = Modifier.weight(1f))
                            MetricBox(label = "Maximum RTT", value = "%.1f ms".format(pingState.maxMs), color = Slate400, modifier = Modifier.weight(1f))
                        }

                        HorizontalDivider(color = Slate700)

                        Text("Latency Line Chart", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        
                        PingJitterChart(
                            latencies = pingState.latencies,
                            modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Slate900.copy(alpha = 0.5f))
            .border(1.dp, Slate700, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun PingJitterChart(latencies: List<PingLatency>, modifier: Modifier = Modifier) {
    val activeLatencies = latencies.mapNotNull { it.latencyMs }
    if (latencies.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No latency logs yet. Wait for engine...", color = Slate400, fontSize = 12.sp)
        }
        return
    }

    val maxVal = (activeLatencies.maxOrNull() ?: 100.0).coerceAtLeast(50.0) * 1.2
    val minVal = 0.0

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val sizeCount = latencies.size

        // Draw horizontal grid lines
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = height - (i * (height / gridCount))
            drawLine(
                color = Slate700.copy(alpha = 0.4f),
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val points = latencies.mapIndexed { index, ping ->
            val x = (index.toFloat() / (sizeCount - 1).coerceAtLeast(1)) * width
            val lat = ping.latencyMs
            val y = if (lat != null) {
                height - (((lat - minVal) / (maxVal - minVal)).toFloat() * height)
            } else {
                height
            }
            androidx.compose.ui.geometry.Offset(x, y)
        }

        val path = androidx.compose.ui.graphics.Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
        }

        drawPath(
            path = path,
            color = Teal500,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )

        points.forEachIndexed { index, offset ->
            val isLoss = latencies[index].latencyMs == null
            drawCircle(
                color = if (isLoss) Red500 else Teal500,
                radius = if (isLoss) 5.dp.toPx() else 4.dp.toPx(),
                center = offset
            )
        }
    }
}

@Composable
fun TracerouteTab(
    state: NetworkState,
    viewModel: MainScreenViewModel
) {
    val traceState = state.tracerouteState
    var targetInput by remember { mutableStateOf("8.8.8.8") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Network Traceroute",
                color = Slate50,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
            Text(
                text = "Trace hop-by-hop packet routing path to target",
                color = Slate400,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Trace Route Destination", color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { if (traceState.status != TracerouteStatus.RUNNING) targetInput = it },
                        label = { Text("IP or Hostname") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate50,
                            focusedBorderColor = Teal500,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = Teal500
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (traceState.status == TracerouteStatus.RUNNING) {
                            Button(
                                onClick = { viewModel.cancelTraceroute() },
                                colors = ButtonDefaults.buttonColors(containerColor = Red500),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Stop Traceroute", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.resetTraceroute() },
                                border = BorderStroke(1.dp, Slate700),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset Hops", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.startTraceroute(targetInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Start Route Trace", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (traceState.status == TracerouteStatus.RUNNING) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Teal500, strokeWidth = 2.dp)
                    Text("Tracing hop ${traceState.scannedHops} of ${traceState.totalHops}...", color = Slate400, fontSize = 13.sp)
                }
            }
        }

        if (traceState.hops.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No hops tracked yet. Press Start to execute trace.", color = Slate400, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(traceState.hops) { hop ->
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (hop.isResponsive) Teal500.copy(alpha = 0.2f) else Slate700),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = hop.hopNumber.toString(),
                                    color = if (hop.isResponsive) Teal500 else Slate400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                if (hop.isResponsive) {
                                    Text(
                                        text = hop.ip,
                                        color = Slate50,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = hop.hostname ?: "No reverse DNS lookup name",
                                        color = Slate400,
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text(
                                        text = "* * * (Request Timeout)",
                                        color = Slate400,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (hop.isResponsive) {
                            Text(
                                text = "${hop.latencyMs} ms",
                                color = Teal500,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GuardTab(
    state: NetworkState,
    viewModel: MainScreenViewModel
) {
    val guardState = state.guardState
    val context = LocalContext.current
    val logsListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Scroll to bottom when new logs arrive
    LaunchedEffect(guardState.logs.size) {
        if (guardState.logs.isNotEmpty()) {
            logsListState.animateScrollToItem(guardState.logs.size - 1)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Premium Guard Protection",
                color = Slate50,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
            Text(
                text = "Configure automated background sentinel watchdogs",
                color = Slate400,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Live Sentinel Indicators Card
        if (guardState.vpnWatchdogEnabled || guardState.wifiIntruderEnabled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Teal500.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Emerald500))
                            Text("ACTIVE SENTINELS ON DUTY", color = Emerald500, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        if (guardState.vpnWatchdogEnabled) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("🛡️ VPN watchdog", color = Slate50, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(guardState.vpnStatus, color = Teal500, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        if (guardState.wifiIntruderEnabled) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("🚨 Wi-Fi subnet sentinel", color = Slate50, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(guardState.wifiStatus, color = Teal500, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        // VPN Guard Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🛡️", fontSize = 20.sp)
                            Column {
                                Text("VPN Leak Watchdog", color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Protects against exposed public traffic", color = Slate400, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = guardState.vpnWatchdogEnabled,
                            onCheckedChange = { viewModel.toggleVpnWatchdog(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Slate800,
                                checkedTrackColor = Emerald500,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Slate700
                            )
                        )
                    }

                    HorizontalDivider(color = Slate700)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate900.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Monitors system network interface changes in background. Instantly pushes a notification alert and blocks cleartext cellular queries if your active VPN tunnel drops while connected to untrusted networks.",
                            color = Slate400,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // WiFi Intruder Watchdog Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🚨", fontSize = 20.sp)
                            Column {
                                Text("WiFi Intruder Detector", color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Monitors new connected mac addresses", color = Slate400, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = guardState.wifiIntruderEnabled,
                            onCheckedChange = { viewModel.toggleWifiIntruder(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Slate800,
                                checkedTrackColor = Emerald500,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Slate700
                            )
                        )
                    }

                    HorizontalDivider(color = Slate700)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate900.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Pings Wi-Fi subnet in background periodically (every 10 minutes) and references MAC OUI vendor maps. Triggers high priority alarms if an unknown MAC address / alien client joins your personal Wi-Fi network.",
                            color = Slate400,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Live Event Console Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sentinel Console Log", color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        
                        if (guardState.wifiIntruderEnabled) {
                            Button(
                                onClick = { viewModel.simulateIntruderAlert() },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple500),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Simulate Alert", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF020617)) // Slate950 extra dark terminal look
                            .border(1.dp, Slate700, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        if (guardState.logs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Console idle. Enable watchdogs above to see live events...", color = Slate400, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                state = logsListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(guardState.logs) { log ->
                                    val color = when (log.type) {
                                        "ALERT" -> Red500
                                        "WARNING" -> Amber500
                                        else -> Teal500
                                    }
                                    val label = when (log.type) {
                                        "ALERT" -> "[ALERT]"
                                        "WARNING" -> "[WARN]"
                                        else -> "[INFO]"
                                    }
                                    
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "${log.timestamp} ",
                                            color = Slate400,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "$label ",
                                            color = color,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = log.message,
                                            color = Slate50,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPONENT DIALOGS & OVERLAYS
// ==========================================

@Composable
fun PortScannerDialog(
    ip: String,
    state: NetworkState,
    viewModel: MainScreenViewModel,
    onDismiss: () -> Unit
) {
    val portScanState = state.portScanState
    var selectedProfileIndex by remember { mutableStateOf(0) }
    val profiles = listOf("Common (18)", "Web (6)", "Admin (6)", "Custom")
    var customPortsInput by remember { mutableStateOf("21,22,80,443,8080") }

    val resolvedPorts = when (selectedProfileIndex) {
        0 -> listOf(21, 22, 23, 25, 53, 80, 110, 135, 139, 143, 443, 445, 1433, 3306, 3389, 5900, 8080, 8443)
        1 -> listOf(80, 443, 8080, 8443, 8888, 9000)
        2 -> listOf(22, 23, 389, 445, 3389, 5900)
        else -> customPortsInput.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.80f)
                .shadow(12.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Slate800)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Advanced Port Prober",
                        color = Slate50,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                HorizontalDivider(color = Slate700)

                DetailRow(label = "Target Host IP", value = ip)

                if (portScanState.status != PortScanStatus.SCANNING) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Probe profile", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Slate900.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            profiles.forEachIndexed { idx, name ->
                                val selected = idx == selectedProfileIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) Teal500 else Color.Transparent)
                                        .clickable { selectedProfileIndex = idx }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        color = if (selected) Slate900 else Slate50,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (selectedProfileIndex == 3) {
                        OutlinedTextField(
                            value = customPortsInput,
                            onValueChange = { customPortsInput = it },
                            label = { Text("Comma-separated ports list") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Slate50,
                                unfocusedTextColor = Slate50,
                                focusedBorderColor = Teal500,
                                unfocusedBorderColor = Slate700,
                                focusedLabelColor = Teal500
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                when (portScanState.status) {
                    PortScanStatus.IDLE -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = { viewModel.startPortScanCustom(ip, resolvedPorts) },
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Start Port Sweep", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    PortScanStatus.SCANNING -> {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            CircularProgressIndicator(color = Teal500)
                            Text(
                                text = "Probing ports: ${portScanState.scannedCount} of ${portScanState.totalCount} checked",
                                color = Slate50,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Discovered ${portScanState.openPorts.size} open ports",
                                color = Slate400,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = { viewModel.cancelPortScan() },
                                colors = ButtonDefaults.buttonColors(containerColor = Red500),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Scan", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    PortScanStatus.FINISHED, PortScanStatus.ERROR -> {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (portScanState.status == PortScanStatus.ERROR) {
                                Text(text = "Error: ${portScanState.errorMessage}", color = Red500, fontSize = 12.sp)
                            } else {
                                Text(
                                    text = "Scan Completed! Found ${portScanState.openPorts.size} open services",
                                    color = Emerald500,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate900.copy(alpha = 0.5f))
                                    .border(1.dp, Slate700, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (portScanState.openPorts.isEmpty()) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                            Text("No open ports found on this device.", color = Slate400, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        }
                                    }
                                } else {
                                    items(portScanState.openPorts) { p ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().border(1.dp, Slate700, RoundedCornerShape(8.dp)),
                                            colors = CardDefaults.cardColors(containerColor = Slate800),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Port ${p.port} (${p.serviceName})",
                                                        color = Slate50,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(text = "🟢 OPEN", color = Emerald500, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                if (p.banner != null) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Slate900.copy(alpha = 0.6f))
                                                            .padding(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Banner: ${p.banner}",
                                                            color = Teal500,
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.resetPortScan() },
                                    border = BorderStroke(1.dp, Slate700),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reset Sweep", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.startPortScanCustom(ip, resolvedPorts) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Probe Again", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// STATIC UI COMPONENTS
// ==========================================

@Composable
fun DashboardQuickIpsCard(
    state: NetworkState,
    onScanTrigger: (String, String, Int, InterfaceType) -> Unit
) {
    val quick = state.quickIps
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val flag = getFlagEmoji(quick.publicCountryCode)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Dashboard Overview",
                color = Slate50,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )

            HorizontalDivider(color = Slate700)

            // Public IP Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate900.copy(alpha = 0.4f))
                    .clickable {
                        if (!quick.isFetchingPublic && quick.publicIp != "Fetching...") {
                            clipboard.setText(AnnotatedString(quick.publicIp))
                            Toast.makeText(context, "Copied Public IP: ${quick.publicIp}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🌐", fontSize = 16.sp)
                    Text("Public IP", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (quick.isFetchingPublic) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Teal500)
                    } else {
                        Text(
                            text = quick.publicIp,
                            color = Teal500,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        if (quick.publicCountry.isNotEmpty()) {
                            Text(
                                text = "$flag ${quick.publicCountry}",
                                color = Slate400,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Wi-Fi Local IP Row
            val hasWifi = quick.wifiIp != "Not Connected"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate900.copy(alpha = 0.4f))
                    .clickable {
                        if (hasWifi) {
                            clipboard.setText(AnnotatedString(quick.wifiIp))
                            Toast.makeText(context, "Copied Wi-Fi IP: ${quick.wifiIp}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📶", fontSize = 16.sp)
                    Text("Wi-Fi IP", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = quick.wifiIp,
                        color = if (hasWifi) Slate50 else Slate400,
                        fontSize = 13.sp,
                        fontWeight = if (hasWifi) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Mobile IP Row
            val hasMobile = quick.mobileIp != "Not Connected"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate900.copy(alpha = 0.4f))
                    .clickable {
                        if (hasMobile) {
                            clipboard.setText(AnnotatedString(quick.mobileIp))
                            Toast.makeText(context, "Copied Mobile IP: ${quick.mobileIp}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📱", fontSize = 16.sp)
                    Text("Mobile IP", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = quick.mobileIp,
                        color = if (hasMobile) Slate50 else Slate400,
                        fontSize = 13.sp,
                        fontWeight = if (hasMobile) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // VPN IP Row
            val hasVpn = quick.vpnIp != "Not Connected"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate900.copy(alpha = 0.4f))
                    .clickable {
                        if (hasVpn) {
                            clipboard.setText(AnnotatedString(quick.vpnIp))
                            Toast.makeText(context, "Copied VPN IP: ${quick.vpnIp}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🛡️", fontSize = 16.sp)
                    Text("VPN IP", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = quick.vpnIp,
                        color = if (hasVpn) Emerald500 else Slate400,
                        fontSize = 13.sp,
                        fontWeight = if (hasVpn) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderSection(onRefresh: () -> Unit, isRefreshing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Wifi Guard",
                    color = Teal500,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.linearGradient(listOf(Teal500, Indigo500)))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PREMIUM",
                        color = Slate900,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text(
                text = "Android Network Diagnostics & Security",
                color = Slate400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier
                .clip(CircleShape)
                .background(Slate800)
                .border(1.dp, Slate700, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Info",
                tint = if (isRefreshing) Slate400 else Teal500
            )
        }
    }
}

@Composable
fun ActiveConnectionCard(
    state: NetworkState,
    onScanClick: () -> Unit
) {
    val active = state.activeConnection
    
    val (title, icon, gradientColors, statusText) = when (active.type) {
        ConnectionType.WIFI -> Quadruple(
            "Wi-Fi Connection",
            Icons.Default.Info,
            listOf(Teal500, Indigo500),
            "Active and Connected"
        )
        ConnectionType.MOBILE -> Quadruple(
            "Cellular Network",
            Icons.Default.Info,
            listOf(Indigo500, Purple500),
            "Active and Connected"
        )
        ConnectionType.VPN -> Quadruple(
            "VPN Interface Active",
            Icons.Default.Lock,
            listOf(Emerald500, Teal500),
            "Connection Tunneled & Secured"
        )
        ConnectionType.OTHER -> Quadruple(
            "Other Connection",
            Icons.Default.Info,
            listOf(Slate700, Slate400),
            "Active Connection"
        )
        ConnectionType.NONE -> Quadruple(
            "No Active Connection",
            Icons.Default.Warning,
            listOf(Red500, Amber500),
            "Disconnected"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.horizontalGradient(gradientColors), alpha = 0.08f)
                .border(1.dp, Brush.horizontalGradient(gradientColors), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(gradientColors.first().copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = gradientColors.first(),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Column {
                            Text(
                                text = title,
                                color = Slate50,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = statusText,
                                color = gradientColors.first(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (active.type != ConnectionType.NONE) {
                    HorizontalDivider(color = Slate700, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Interface", color = Slate400, fontSize = 13.sp)
                        Text(
                            text = active.interfaceName ?: "Unknown",
                            color = Slate50,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (active.ipAddresses.isNotEmpty()) {
                        Text(
                            text = "IP Addresses (Click to Copy)",
                            color = Slate400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        
                        val clipboard = LocalClipboardManager.current
                        val context = LocalContext.current
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            active.ipAddresses.forEach { ip ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Slate900.copy(alpha = 0.5f))
                                        .clickable {
                                            clipboard.setText(AnnotatedString(ip))
                                            Toast.makeText(context, "Copied: $ip", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (ip.contains(":")) "IPv6" else "IPv4",
                                        color = if (ip.contains(":")) Indigo500 else Teal500,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = ip,
                                        color = Slate50,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DnsLeakTestCard(
    state: NetworkState,
    onRunTest: () -> Unit,
    onClearTest: () -> Unit,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Amber500.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Amber500,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        text = "DNS Leak Test",
                        color = Slate50,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Verify DNS queries do not leak ISP details",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }
            }

            if (!state.isTestingDns && state.dnsLeakTestResult == null) {
                Button(
                    onClick = onRunTest,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Run DNS Leak Test", fontWeight = FontWeight.Bold)
                }
            } else if (state.isTestingDns) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Teal500)
                    Text(
                        text = state.dnsTestProgress ?: "Testing...",
                        color = Slate400,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (state.dnsLeakTestResult != null) {
                val result = state.dnsLeakTestResult
                val isLeaking = result.conclusion.contains("leak", ignoreCase = true)
                
                val bannerColor = if (isLeaking) Red500 else Emerald500
                val bannerText = if (isLeaking) "DNS LEAK DETECTED!" else "SECURE: No DNS Leaks Detected"
                val bannerIcon = if (isLeaking) Icons.Default.Warning else Icons.Default.Check

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bannerColor.copy(alpha = 0.15f))
                        .border(1.dp, bannerColor, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = bannerIcon, contentDescription = null, tint = bannerColor)
                        Text(
                            text = bannerText,
                            color = bannerColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Public IP Check Result",
                        color = Slate50,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    DetailRow(label = "IP Address", value = result.publicIp ?: "Not detected")
                    DetailRow(label = "Country", value = result.publicIpCountry ?: "Unknown")
                    DetailRow(label = "ISP / ASN", value = result.publicIpAsn ?: "Unknown")
                }

                Button(
                    onClick = { openWebPage(context, "https://ipleak.net") },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Verify in Browser",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify IP & Location in Browser", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                HorizontalDivider(color = Slate700)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Detected DNS Servers (${result.dnsServers.size})",
                        color = Slate50,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (result.dnsServers.isEmpty()) {
                        Text(
                            text = "No DNS servers detected.",
                            color = Slate400,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        result.dnsServers.forEach { dns ->
                            DnsServerItem(dns)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearTest,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Slate700),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Reset", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onRunTest,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Test Again", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Slate400, fontSize = 12.sp)
        Text(
            text = value,
            color = Slate50,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun DnsServerItem(dns: DnsResolverInfo) {
    val flag = getFlagEmoji(dns.country)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Slate900.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dns.ip,
                    color = Slate50,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$flag ${dns.countryName ?: dns.country ?: ""}",
                    color = Slate400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            dns.asn?.let {
                Text(
                    text = it,
                    color = Teal500,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ExpandableInterfaceCategory(
    type: InterfaceType,
    interfaces: List<NetworkInterfaceInfo>,
    onScanClick: (NetworkInterfaceInfo) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val (title, icon, accentColor) = when (type) {
        InterfaceType.WIFI -> Triple("Wi-Fi Interfaces (${interfaces.size})", Icons.Default.Info, Teal500)
        InterfaceType.MOBILE -> Triple("Cellular Interfaces (${interfaces.size})", Icons.Default.Info, Indigo500)
        InterfaceType.VPN -> Triple("VPN / Tunnel Interfaces (${interfaces.size})", Icons.Default.Lock, Emerald500)
        InterfaceType.LOOPBACK -> Triple("Loopback Interfaces (${interfaces.size})", Icons.Default.Refresh, Slate400)
        InterfaceType.OTHER -> Triple("Other Interfaces (${interfaces.size})", Icons.Default.Info, Amber500)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate700, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        color = Slate50,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Slate400
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate900.copy(alpha = 0.3f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    interfaces.forEach { networkInterface ->
                        InterfaceItem(networkInterface, onScanClick)
                    }
                }
            }
        }
    }
}

@Composable
fun InterfaceItem(
    info: NetworkInterfaceInfo,
    onScanClick: (NetworkInterfaceInfo) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Slate800.copy(alpha = 0.5f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = info.name,
                    color = Teal500,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (info.displayName != info.name) {
                    Text(
                        text = "(${info.displayName})",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (info.ipv4Addresses.isNotEmpty()) {
            Text(text = "IPv4 Addresses", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            info.ipv4Addresses.forEach { ip ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Slate900.copy(alpha = 0.4f))
                        .clickable {
                            clipboard.setText(AnnotatedString(ip))
                            Toast.makeText(context, "Copied: $ip", Toast.LENGTH_SHORT).show()
                        }
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = ip, color = Slate50, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "📋", fontSize = 10.sp)
                }
            }
        }

        if (info.ipv6Addresses.isNotEmpty()) {
            Text(text = "IPv6 Addresses", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            info.ipv6Addresses.forEach { ip ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Slate900.copy(alpha = 0.4f))
                        .clickable {
                            clipboard.setText(AnnotatedString(ip))
                            Toast.makeText(context, "Copied: $ip", Toast.LENGTH_SHORT).show()
                        }
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = ip,
                        color = Slate50,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    Text(text = "📋", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun CoffeeDonationCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clickable { openWebPage(context, "https://buymeacoffee.com/whynotme2") },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.horizontalGradient(listOf(Color(0xFFFFDD00), Color(0xFFF1C40F))), alpha = 0.95f)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF111111),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "☕ Buy Me a Coffee",
                color = Color(0xFF111111),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

fun openWebPage(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not launch web browser", Toast.LENGTH_SHORT).show()
    }
}

fun getFlagEmoji(countryCode: String?): String {
    if (countryCode == null || countryCode.isEmpty() || countryCode.length != 2) return "🌐"
    return try {
        val uppercaseCode = countryCode.uppercase()
        val firstChar = Character.codePointAt(uppercaseCode, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(uppercaseCode, 1) - 0x41 + 0x1F1E6
        String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    } catch (e: Exception) {
        "🌐"
    }
}

private data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
fun WifiSignalGauge(signalState: WifiSignalState, modifier: Modifier = Modifier) {
    val rssi = signalState.rssi
    val signalLevel = signalState.signalLevel
    val qualityText = signalState.qualityText
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wi-Fi Signal Analyzer",
                    color = Slate50,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (signalState.isConnected) Emerald500.copy(alpha = 0.2f) else Red500.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (signalState.isConnected) "ACTIVE" else "OFFLINE",
                        color = if (signalState.isConnected) Emerald500 else Red500,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (signalState.isConnected) {
                val animProgress = remember { Animatable(0f) }
                LaunchedEffect(rssi) {
                    val normal = ((rssi + 100).toFloat() / 50f).coerceIn(0f, 1f)
                    animProgress.animateTo(
                        targetValue = normal,
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
                    )
                }
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(150.dp)
                ) {
                    Canvas(modifier = Modifier.size(140.dp)) {
                        val sweepAngle = 240f
                        val startAngle = 150f
                        
                        drawArc(
                            color = Slate700,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 10.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                        
                        val color = when (signalLevel) {
                            4 -> Emerald500
                            3 -> Teal500
                            2 -> Amber500
                            else -> Red500
                        }
                        
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle * animProgress.value,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 10.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$rssi dBm",
                            color = Slate50,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = qualityText,
                            color = when (signalLevel) {
                                4 -> Emerald500
                                3 -> Teal500
                                2 -> Amber500
                                else -> Red500
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SSID", color = Slate400, fontSize = 11.sp)
                        Text(signalState.ssid, color = Slate50, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Link Speed", color = Slate400, fontSize = 11.sp)
                        Text("${signalState.linkSpeedMbps} Mbps", color = Slate50, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Frequency", color = Slate400, fontSize = 11.sp)
                        val freqText = if (signalState.frequencyMhz > 4900) "5 GHz" else "2.4 GHz"
                        Text(freqText, color = Slate50, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Connect to Wi-Fi to monitor signal level in real-time.",
                        color = Slate400,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun WakeOnLanCard(viewModel: MainScreenViewModel) {
    var macAddress by remember { mutableStateOf("") }
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Wake-on-LAN (WoL) Magic Broadcaster",
                color = Slate50,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Send a UDP Magic Packet to boot up home servers, NAS devices, or desktop PCs.",
                color = Slate400,
                fontSize = 12.sp
            )
            
            OutlinedTextField(
                value = macAddress,
                onValueChange = { macAddress = it },
                label = { Text("MAC Address (e.g. AA:BB:CC:DD:EE:FF)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Teal500,
                    unfocusedBorderColor = Slate700,
                    focusedLabelColor = Teal500,
                    unfocusedLabelColor = Slate400,
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50
                ),
                shape = RoundedCornerShape(8.dp)
            )
            
            Button(
                onClick = {
                    val cleanMac = macAddress.replace(":", "").replace("-", "").trim()
                    if (cleanMac.length == 12) {
                        viewModel.sendWakeOnLan(macAddress)
                        Toast.makeText(context, "Magic Packet broadcasted for $macAddress", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Please enter a valid 12-character MAC Address", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Wake-on-LAN Packet", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DiagnosticsTab(state: NetworkState, viewModel: MainScreenViewModel) {
    var subTab by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = Slate900,
            contentColor = Teal500
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("Ping Jitter", color = if (subTab == 0) Teal500 else Slate400, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("Traceroute", color = if (subTab == 1) Teal500 else Slate400, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            when (subTab) {
                0 -> PingJitterTab(state = state, viewModel = viewModel)
                1 -> TracerouteTab(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun ToolsTab(state: NetworkState, viewModel: MainScreenViewModel, context: android.content.Context) {
    var subTab by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = Slate900,
            contentColor = Teal500
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("DNS Speed Bench", color = if (subTab == 0) Teal500 else Slate400, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("IP Geo & WHOIS", color = if (subTab == 1) Teal500 else Slate400, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            when (subTab) {
                0 -> DnsBenchmarkTab(state = state, viewModel = viewModel)
                1 -> GeoLocateTab(state = state, viewModel = viewModel, context = context)
            }
        }
    }
}

@Composable
fun DnsBenchmarkTab(state: NetworkState, viewModel: MainScreenViewModel) {
    var domainInput by remember { mutableStateOf("google.com") }
    val dnsState = state.dnsBenchmarkState
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("DNS Speed Benchmark", color = Slate50, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Test resolution speed of popular public DNS servers directly from your network.", color = Slate400, fontSize = 12.sp)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = domainInput,
                            onValueChange = { domainInput = it },
                            label = { Text("Domain to resolve") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Teal500,
                                unfocusedBorderColor = Slate700,
                                focusedLabelColor = Teal500,
                                unfocusedLabelColor = Slate400,
                                focusedTextColor = Slate50,
                                unfocusedTextColor = Slate50
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        
                        Button(
                            onClick = { viewModel.startDnsBenchmark(domainInput) },
                            enabled = dnsState.status != DnsBenchmarkStatus.RUNNING,
                            colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        if (dnsState.status == DnsBenchmarkStatus.RUNNING) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = Teal500)
                        Text("Benchmarking DNS servers...", color = Slate400, fontSize = 12.sp)
                    }
                }
            }
        }
        
        if (dnsState.servers.isNotEmpty()) {
            item {
                Text(
                    text = "Benchmark Results (Fastest First)",
                    color = Slate50,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            items(dnsState.servers) { server ->
                val maxLatency = 400f
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Slate700, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(2f)) {
                            Text(server.name, color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(server.ip, color = Slate400, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val latencyVal = server.latencyMs
                            val fraction = if (latencyVal != null) {
                                (latencyVal.toFloat() / maxLatency).coerceIn(0.05f, 1f)
                            } else 1f
                            val barColor = when {
                                !server.isResponsive -> Red500
                                latencyVal != null && latencyVal < 40 -> Emerald500
                                latencyVal != null && latencyVal < 100 -> Teal500
                                latencyVal != null && latencyVal < 200 -> Amber500
                                else -> Red500
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(6.dp)
                                    .background(Slate900, RoundedCornerShape(3.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .background(barColor, RoundedCornerShape(3.dp))
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                            if (!server.isResponsive) {
                                Text("OFFLINE", color = Red500, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("${server.latencyMs} ms", color = Teal500, fontSize = 15.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeoLocateTab(state: NetworkState, viewModel: MainScreenViewModel, context: android.content.Context) {
    var targetInput by remember { mutableStateOf("") }
    val geoState = state.geoLocateState
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("IP Geolocation & WHOIS Resolver", color = Slate50, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Lookup geographic location, ISP registry, and complete WHOIS data for any external IP or hostname.", color = Slate400, fontSize = 12.sp)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = targetInput,
                            onValueChange = { targetInput = it },
                            label = { Text("IP / Host (e.g. 8.8.8.8, empty uses your IP)") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Teal500,
                                unfocusedBorderColor = Slate700,
                                focusedLabelColor = Teal500,
                                unfocusedLabelColor = Slate400,
                                focusedTextColor = Slate50,
                                unfocusedTextColor = Slate50
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        
                        Button(
                            onClick = { viewModel.startGeoLocation(targetInput) },
                            enabled = geoState.status != GeoLocateStatus.RUNNING,
                            colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Search", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        if (geoState.status == GeoLocateStatus.RUNNING) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = Teal500)
                        Text("Locating host server...", color = Slate400, fontSize = 12.sp)
                    }
                }
            }
        }
        
        val result = geoState.result
        if (geoState.status == GeoLocateStatus.FINISHED && result != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Location Results", color = Slate50, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            val flag = getFlagEmoji(result.countryCode)
                            Text("$flag ${result.countryCode}", fontSize = 18.sp)
                        }
                        
                        HorizontalDivider(color = Slate700)
                        
                        DetailRow(label = "IP Address", value = result.ip)
                        DetailRow(label = "Country", value = result.country)
                        DetailRow(label = "Region / City", value = "${result.region} / ${result.city}")
                        DetailRow(label = "Zip Code", value = result.zip)
                        DetailRow(label = "ISP / Org", value = result.isp)
                        DetailRow(label = "Timezone", value = result.timezone)
                        
                        if (result.lat != 0.0 || result.lon != 0.0) {
                            DetailRow(label = "Coordinates", value = "${result.lat}, ${result.lon}")
                            
                            Button(
                                onClick = {
                                    val geoUri = Uri.parse("geo:${result.lat},${result.lon}?q=${result.lat},${result.lon}(${result.ip})")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                    context.startActivity(mapIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open in Google Maps", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Slate700, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Raw WHOIS Registry Data", color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        HorizontalDivider(color = Slate700)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate900, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = result.whoisData,
                                color = Emerald500,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

