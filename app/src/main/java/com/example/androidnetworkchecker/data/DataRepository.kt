package com.example.androidnetworkchecker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll

interface DataRepository {
    val networkState: StateFlow<NetworkState>
    fun refreshNetworkState(context: Context)
    suspend fun fetchPublicIpOverview()
    suspend fun performDnsLeakTest(progressCallback: (String) -> Unit): Result<DnsLeakTestResult>
    fun clearDnsLeakTestResult()
    
    // Subnet Scanner APIs
    suspend fun startSubnetScan(context: Context, interfaceName: String, localIp: String, prefixLength: Int)
    fun cancelSubnetScan()
    fun clearScanState()

    // Guard Watchdog APIs
    fun toggleVpnWatchdog(context: Context, enabled: Boolean)
    fun toggleWifiIntruder(context: Context, enabled: Boolean)
    fun simulateIntruderAlert()

    // Port Scanner APIs
    suspend fun startPortScan(ip: String)
    suspend fun startPortScanCustom(ip: String, ports: List<Int>)
    fun cancelPortScan()
    fun clearPortScanState()

    // Traceroute APIs
    suspend fun startTraceroute(target: String)
    fun cancelTraceroute()
    fun clearTracerouteState()

    // Ping Jitter APIs
    suspend fun startPingJitter(target: String)
    fun cancelPingJitter()
    fun clearPingJitterState()

    // Premium Added Features APIs
    fun refreshWifiSignalState(context: Context)
    fun sendWakeOnLan(macAddress: String)
    suspend fun runDnsBenchmark(domain: String)
    suspend fun runGeoLocation(target: String)
}

class DefaultDataRepository : DataRepository {
    private val _networkState = MutableStateFlow(NetworkState())
    override val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private fun getWifiIpAddress(context: Context): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val ipInt = wm.connectionInfo.ipAddress
            if (ipInt != 0) {
                String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    (ipInt and 0xff),
                    (ipInt shr 8 and 0xff),
                    (ipInt shr 16 and 0xff),
                    (ipInt shr 24 and 0xff)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun refreshNetworkState(context: Context) {
        val interfaces = NetworkManager.getNetworkInterfaces()
        val activeConnection = NetworkManager.getActiveConnectionInfo(context)

        val wifiIp = getWifiIpAddress(context)
            ?: interfaces.firstOrNull { it.type == InterfaceType.WIFI }?.ipv4Addresses?.firstOrNull()
            ?: interfaces.firstOrNull { it.type == InterfaceType.WIFI }?.ipv6Addresses?.firstOrNull()
            ?: "Not Connected"
        val mobileIp = interfaces.firstOrNull { it.type == InterfaceType.MOBILE }?.ipv4Addresses?.firstOrNull()
            ?: interfaces.firstOrNull { it.type == InterfaceType.MOBILE }?.ipv6Addresses?.firstOrNull()
            ?: "Not Connected"
        val vpnIp = interfaces.firstOrNull { it.type == InterfaceType.VPN }?.ipv4Addresses?.firstOrNull()
            ?: interfaces.firstOrNull { it.type == InterfaceType.VPN }?.ipv6Addresses?.firstOrNull()
            ?: "Not Connected"

        _networkState.update { state ->
            state.copy(
                interfaces = interfaces,
                activeConnection = activeConnection,
                quickIps = state.quickIps.copy(
                    wifiIp = wifiIp,
                    mobileIp = mobileIp,
                    vpnIp = vpnIp
                )
            )
        }
    }

    override suspend fun fetchPublicIpOverview() {
        _networkState.update { state ->
            state.copy(
                quickIps = state.quickIps.copy(
                    isFetchingPublic = true, 
                    publicIp = "Fetching..."
                )
            )
        }
        try {
            val details = NetworkManager.fetchPublicIpDetails()
            val ip = details["ip"] ?: "Not Detected"
            val country = details["country"] ?: ""
            val countryCode = details["countryCode"] ?: ""
            val asn = details["asn"] ?: ""
            
            _networkState.update { state ->
                state.copy(
                    quickIps = state.quickIps.copy(
                        publicIp = ip,
                        publicCountry = country,
                        publicCountryCode = countryCode,
                        publicAsn = asn,
                        isFetchingPublic = false
                    )
                )
            }
        } catch (e: Exception) {
            _networkState.update { state ->
                state.copy(
                    quickIps = state.quickIps.copy(
                        publicIp = "Not Detected",
                        isFetchingPublic = false
                    )
                )
            }
        }
    }

    override suspend fun performDnsLeakTest(progressCallback: (String) -> Unit): Result<DnsLeakTestResult> {
        _networkState.update { it.copy(isTestingDns = true, dnsTestProgress = "Initializing test...") }
        return try {
            val result = NetworkManager.runDnsLeakTest { progress ->
                progressCallback(progress)
                _networkState.update { it.copy(dnsTestProgress = progress) }
            }
            _networkState.update {
                it.copy(
                    dnsLeakTestResult = result,
                    isTestingDns = false,
                    dnsTestProgress = null
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            _networkState.update {
                it.copy(
                    isTestingDns = false,
                    dnsTestProgress = "Error: ${e.message}"
                )
            }
            Result.failure(e)
        }
    }

    override fun clearDnsLeakTestResult() {
        _networkState.update {
            it.copy(
                dnsLeakTestResult = null,
                dnsTestProgress = null
            )
        }
    }

    override suspend fun startSubnetScan(context: Context, interfaceName: String, localIp: String, prefixLength: Int) {
        _networkState.update {
            it.copy(
                scanState = ScanState(
                    status = ScanStatus.SCANNING,
                    targetInterface = interfaceName,
                    targetSubnet = "$localIp/$prefixLength",
                    scannedCount = 0,
                    totalCount = 0,
                    activeHosts = emptyList()
                )
            )
        }

        val ips = NetworkManager.getSubnetIps(localIp, prefixLength)
        if (ips.isEmpty()) {
            _networkState.update {
                it.copy(
                    scanState = it.scanState.copy(
                        status = ScanStatus.ERROR,
                        errorMessage = "No valid IPs found to scan."
                    )
                )
            }
            return
        }

        _networkState.update {
            it.copy(
                scanState = it.scanState.copy(
                    totalCount = ips.size
                )
            )
        }

        val activeHostsList = mutableListOf<ScannedHost>()
        val countLock = Any()

        try {
            NetworkManager.scanHosts(context, interfaceName, ips) { scanned, total, foundHost ->
                synchronized(countLock) {
                    if (foundHost != null) {
                        activeHostsList.add(foundHost)
                    }
                    _networkState.update { state ->
                        state.copy(
                            scanState = state.scanState.copy(
                                scannedCount = scanned,
                                activeHosts = activeHostsList.toList()
                            )
                        )
                    }
                }
            }
            _networkState.update {
                it.copy(
                    scanState = it.scanState.copy(
                        status = ScanStatus.FINISHED
                    )
                )
            }
        } catch (e: CancellationException) {
            _networkState.update {
                it.copy(
                    scanState = it.scanState.copy(
                        status = ScanStatus.IDLE,
                        errorMessage = "Scan cancelled"
                    )
                )
            }
            throw e
        } catch (e: Exception) {
            _networkState.update {
                it.copy(
                    scanState = it.scanState.copy(
                        status = ScanStatus.ERROR,
                        errorMessage = e.message ?: "Subnet scan failed"
                    )
                )
            }
        }
    }

    override fun cancelSubnetScan() {
        _networkState.update {
            if (it.scanState.status == ScanStatus.SCANNING) {
                it.copy(
                    scanState = it.scanState.copy(
                        status = ScanStatus.IDLE,
                        errorMessage = "Scan cancelled"
                    )
                )
            } else {
                it
            }
        }
    }

    override fun clearScanState() {
        _networkState.update {
            it.copy(scanState = ScanState())
        }
    }

    override suspend fun startPortScan(ip: String) {
        val commonPorts = listOf(21, 22, 23, 25, 53, 80, 110, 135, 139, 143, 443, 445, 1433, 3306, 3389, 5900, 8080, 8443)
        startPortScanCustom(ip, commonPorts)
    }

    override suspend fun startPortScanCustom(ip: String, ports: List<Int>) {
        _networkState.update { state ->
            state.copy(
                portScanState = PortScanState(
                    status = PortScanStatus.SCANNING,
                    targetIp = ip,
                    scannedCount = 0,
                    totalCount = ports.size,
                    openPorts = emptyList()
                )
            )
        }

        val openPortsList = mutableListOf<ScannedPort>()
        val countLock = Any()

        try {
            NetworkManager.scanPorts(ip, ports) { scanned, total, foundPort ->
                synchronized(countLock) {
                    if (foundPort != null) {
                        openPortsList.add(foundPort)
                    }
                    _networkState.update { state ->
                        state.copy(
                            portScanState = state.portScanState.copy(
                                scannedCount = scanned,
                                openPorts = openPortsList.toList()
                            )
                        )
                    }
                }
            }
            _networkState.update { state ->
                state.copy(portScanState = state.portScanState.copy(status = PortScanStatus.FINISHED))
            }
        } catch (e: CancellationException) {
            _networkState.update { state ->
                state.copy(
                    portScanState = state.portScanState.copy(
                        status = PortScanStatus.IDLE,
                        errorMessage = "Scan cancelled"
                    )
                )
            }
            throw e
        } catch (e: Exception) {
            _networkState.update { state ->
                state.copy(
                    portScanState = state.portScanState.copy(
                        status = PortScanStatus.ERROR,
                        errorMessage = e.message ?: "Port scan failed"
                    )
                )
            }
        }
    }

    override fun cancelPortScan() {
        _networkState.update { state ->
            if (state.portScanState.status == PortScanStatus.SCANNING) {
                state.copy(portScanState = state.portScanState.copy(status = PortScanStatus.IDLE, errorMessage = "Scan cancelled"))
            } else {
                state
            }
        }
    }

    override fun clearPortScanState() {
        _networkState.update { state ->
            state.copy(portScanState = PortScanState())
        }
    }

    override suspend fun startTraceroute(target: String) {
        _networkState.update { state ->
            state.copy(
                tracerouteState = TracerouteState(
                    status = TracerouteStatus.RUNNING,
                    target = target,
                    hops = emptyList(),
                    scannedHops = 0,
                    totalHops = 30
                )
            )
        }
        
        val hopsList = mutableListOf<TracerouteHop>()
        val countLock = Any()
        
        try {
            NetworkManager.runTraceroute(target, 30) { hop ->
                synchronized(countLock) {
                    hopsList.add(hop)
                    _networkState.update { state ->
                        state.copy(
                            tracerouteState = state.tracerouteState.copy(
                                scannedHops = hop.hopNumber,
                                hops = hopsList.toList()
                            )
                        )
                    }
                }
            }
            _networkState.update { state ->
                state.copy(
                    tracerouteState = state.tracerouteState.copy(
                        status = TracerouteStatus.FINISHED
                    )
                )
            }
        } catch (e: CancellationException) {
            _networkState.update { state ->
                state.copy(
                    tracerouteState = state.tracerouteState.copy(
                        status = TracerouteStatus.IDLE,
                        errorMessage = "Traceroute cancelled"
                    )
                )
            }
            throw e
        } catch (e: Exception) {
            _networkState.update { state ->
                state.copy(
                    tracerouteState = state.tracerouteState.copy(
                        status = TracerouteStatus.ERROR,
                        errorMessage = e.message ?: "Traceroute failed"
                    )
                )
            }
        }
    }

    override fun cancelTraceroute() {
        _networkState.update { state ->
            if (state.tracerouteState.status == TracerouteStatus.RUNNING) {
                state.copy(
                    tracerouteState = state.tracerouteState.copy(
                        status = TracerouteStatus.IDLE,
                        errorMessage = "Traceroute cancelled"
                    )
                )
            } else {
                state
            }
        }
    }

    override fun clearTracerouteState() {
        _networkState.update { state ->
            state.copy(tracerouteState = TracerouteState())
        }
    }

    override suspend fun startPingJitter(target: String) {
        _networkState.update { state ->
            state.copy(
                pingJitterState = PingJitterState(
                    status = PingJitterStatus.RUNNING,
                    target = target,
                    latencies = emptyList()
                )
            )
        }
        
        val latenciesList = mutableListOf<PingLatency>()
        val countLock = Any()
        
        try {
            while (true) {
                val latency = NetworkManager.runSinglePing(target)
                val pingLat = PingLatency(latency, System.currentTimeMillis())
                
                synchronized(countLock) {
                    latenciesList.add(pingLat)
                    if (latenciesList.size > 30) {
                        latenciesList.removeAt(0)
                    }
                    
                    val activeLatencies = latenciesList.filter { it.latencyMs != null }.map { it.latencyMs!! }
                    val totalSent = latenciesList.size
                    val totalLoss = latenciesList.count { it.latencyMs == null }
                    val lossPercent = (totalLoss.toDouble() / totalSent.toDouble()) * 100.0
                    
                    val min = if (activeLatencies.isNotEmpty()) activeLatencies.minOrNull() ?: 0.0 else 0.0
                    val max = if (activeLatencies.isNotEmpty()) activeLatencies.maxOrNull() ?: 0.0 else 0.0
                    val avg = if (activeLatencies.isNotEmpty()) activeLatencies.average() else 0.0
                    
                    var jitter = 0.0
                    if (activeLatencies.size > 1) {
                        var diffSum = 0.0
                        for (i in 1 until activeLatencies.size) {
                            diffSum += Math.abs(activeLatencies[i] - activeLatencies[i-1])
                        }
                        jitter = diffSum / (activeLatencies.size - 1)
                    }
                    
                    _networkState.update { state ->
                        state.copy(
                            pingJitterState = state.pingJitterState.copy(
                                latencies = latenciesList.toList(),
                                minMs = min,
                                maxMs = max,
                                avgMs = avg,
                                jitterMs = jitter,
                                packetLossPercent = lossPercent
                            )
                        )
                    }
                }
                kotlinx.coroutines.delay(1000)
            }
        } catch (e: CancellationException) {
            _networkState.update { state ->
                state.copy(
                    pingJitterState = state.pingJitterState.copy(
                        status = PingJitterStatus.IDLE,
                        errorMessage = "Ping test stopped"
                    )
                )
            }
            throw e
        } catch (e: Exception) {
            _networkState.update { state ->
                state.copy(
                    pingJitterState = state.pingJitterState.copy(
                        status = PingJitterStatus.ERROR,
                        errorMessage = e.message ?: "Ping test failed"
                    )
                )
            }
        }
    }

    override fun cancelPingJitter() {
        _networkState.update { state ->
            if (state.pingJitterState.status == PingJitterStatus.RUNNING) {
                state.copy(
                    pingJitterState = state.pingJitterState.copy(
                        status = PingJitterStatus.FINISHED
                    )
                )
            } else {
                state
            }
        }
    }

    override fun clearPingJitterState() {
        _networkState.update { state ->
            state.copy(pingJitterState = PingJitterState())
        }
    }

    private val repositoryScope = CoroutineScope(Dispatchers.Default)
    private var vpnWatchdogJob: Job? = null
    private var wifiIntruderJob: Job? = null

    private fun addGuardLog(type: String, message: String) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timestamp = sdf.format(java.util.Date())
        val entry = GuardLogEntry(timestamp = timestamp, message = message, type = type)
        _networkState.update { state ->
            val updatedLogs = (state.guardState.logs + entry).takeLast(40)
            state.copy(guardState = state.guardState.copy(logs = updatedLogs))
        }
    }

    override fun toggleVpnWatchdog(context: Context, enabled: Boolean) {
        _networkState.update { state ->
            state.copy(
                guardState = state.guardState.copy(
                    vpnWatchdogEnabled = enabled,
                    vpnStatus = if (enabled) "Active - Monitoring tunnel" else "Inactive"
                )
            )
        }
        vpnWatchdogJob?.cancel()
        if (enabled) {
            vpnWatchdogJob = repositoryScope.launch {
                addGuardLog("INFO", "VPN Leak Sentinel activated. Shield ready.")
                try {
                    while (true) {
                        delay(8000)
                        val activeConnection = NetworkManager.getActiveConnectionInfo(context)
                        if (activeConnection.type == ConnectionType.VPN) {
                            addGuardLog("INFO", "VPN tunnel is secure (${activeConnection.interfaceName ?: "tun0"}).")
                        } else {
                            addGuardLog("WARNING", "VPN tunnel dropped! Defaulting to unencrypted routes.")
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        addGuardLog("INFO", "VPN Sentinel deactivated.")
                    }
                }
            }
        } else {
            addGuardLog("INFO", "VPN Sentinel deactivated by user.")
        }
    }

    override fun toggleWifiIntruder(context: Context, enabled: Boolean) {
        _networkState.update { state ->
            state.copy(
                guardState = state.guardState.copy(
                    wifiIntruderEnabled = enabled,
                    wifiStatus = if (enabled) "Active - Guarding subnet" else "Inactive"
                )
            )
        }
        wifiIntruderJob?.cancel()
        if (enabled) {
            wifiIntruderJob = repositoryScope.launch {
                addGuardLog("INFO", "WiFi Intruder Sentinel activated. Establishing baseline...")
                try {
                    val interfaces = NetworkManager.getNetworkInterfaces()
                    val wifiInfo = interfaces.firstOrNull { it.type == InterfaceType.WIFI }
                    if (wifiInfo == null) {
                        addGuardLog("WARNING", "Wi-Fi is not active. Sentinel paused.")
                        return@launch
                    }
                    val localIp = wifiInfo.ipv4Addresses.firstOrNull() ?: "192.168.1.100"
                    val prefix = wifiInfo.prefixLength
                    val interfaceName = wifiInfo.name
                    
                    addGuardLog("INFO", "Scanning subnet $localIp/$prefix on $interfaceName...")
                    
                    val ips = NetworkManager.getSubnetIps(localIp, prefix)
                    val baselineHosts = mutableSetOf<String>()
                    
                    NetworkManager.scanHosts(context, interfaceName, ips) { _, _, foundHost ->
                        if (foundHost != null) {
                            baselineHosts.add(foundHost.ip)
                        }
                    }
                    
                    addGuardLog("INFO", "Baseline established. Found ${baselineHosts.size} authorized devices.")
                    
                    while (true) {
                        delay(30000)
                        addGuardLog("INFO", "Periodic subnet sweep running...")
                        
                        val activeHosts = mutableSetOf<String>()
                        NetworkManager.scanHosts(context, interfaceName, ips) { _, _, foundHost ->
                            if (foundHost != null) {
                                activeHosts.add(foundHost.ip)
                            }
                        }
                        
                        val newHosts = activeHosts - baselineHosts
                        if (newHosts.isNotEmpty()) {
                            for (host in newHosts) {
                                addGuardLog("ALERT", "Intruder Alert! Unknown device detected at IP $host!")
                                baselineHosts.add(host)
                            }
                        } else {
                            addGuardLog("INFO", "Subnet sweep completed. Status: Secure.")
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        addGuardLog("INFO", "WiFi Sentinel deactivated.")
                    } else {
                        addGuardLog("WARNING", "WiFi Sentinel error: ${e.message}")
                    }
                }
            }
        } else {
            addGuardLog("INFO", "WiFi Sentinel deactivated by user.")
        }
    }

    override fun simulateIntruderAlert() {
        repositoryScope.launch {
            addGuardLog("WARNING", "Subnet sweep triggered by user simulation...")
            delay(1500)
            val randomIp = "192.168.1." + (10..254).random()
            val randomMac = (1..6).map { "%02X".format((0..255).random()) }.joinToString(":")
            addGuardLog("ALERT", "Intruder Alert! Alien device found at IP $randomIp (MAC: $randomMac). Potential MITM danger detected!")
        }
    }

    override fun refreshWifiSignalState(context: Context) {
        val wifiSignal = NetworkManager.getWifiSignalInfo(context)
        _networkState.update { it.copy(wifiSignalState = wifiSignal) }
    }

    override fun sendWakeOnLan(macAddress: String) {
        repositoryScope.launch(Dispatchers.IO) {
            NetworkManager.sendWakeOnLan(macAddress)
        }
    }

    override suspend fun runDnsBenchmark(domain: String) {
        _networkState.update { state ->
            state.copy(
                dnsBenchmarkState = DnsBenchmarkState(
                    status = DnsBenchmarkStatus.RUNNING,
                    targetDomain = domain,
                    servers = listOf(
                        DnsBenchmarkServer("Google DNS", "8.8.8.8", null),
                        DnsBenchmarkServer("Cloudflare", "1.1.1.1", null),
                        DnsBenchmarkServer("Quad9", "9.9.9.9", null),
                        DnsBenchmarkServer("OpenDNS", "208.67.222.222", null),
                        DnsBenchmarkServer("AdGuard DNS", "94.140.14.14", null),
                        DnsBenchmarkServer("Level3 DNS", "4.2.2.2", null)
                    )
                )
            )
        }
        
        try {
            val currentServers = _networkState.value.dnsBenchmarkState.servers
            val results = mutableListOf<DnsBenchmarkServer>()
            
            coroutineScope {
                val jobs = currentServers.map { server ->
                    launch(Dispatchers.IO) {
                        val latency = NetworkManager.queryDnsServer(server.ip, domain)
                        val resolvedServer = if (latency >= 0) {
                            server.copy(latencyMs = latency, isResponsive = true)
                        } else {
                            server.copy(latencyMs = null, isResponsive = false)
                        }
                        synchronized(results) {
                            results.add(resolvedServer)
                        }
                        
                        val sortedResults = results.sortedWith(
                            compareBy({ !it.isResponsive }, { it.latencyMs ?: Long.MAX_VALUE })
                        )
                        _networkState.update { state ->
                            state.copy(
                                dnsBenchmarkState = state.dnsBenchmarkState.copy(
                                    servers = sortedResults
                                )
                            )
                        }
                    }
                }
                jobs.joinAll()
            }
            
            _networkState.update { state ->
                state.copy(
                    dnsBenchmarkState = state.dnsBenchmarkState.copy(
                        status = DnsBenchmarkStatus.FINISHED
                    )
                )
            }
        } catch (e: Exception) {
            _networkState.update { state ->
                state.copy(
                    dnsBenchmarkState = state.dnsBenchmarkState.copy(
                        status = DnsBenchmarkStatus.ERROR,
                        errorMessage = e.message ?: "Failed DNS benchmark"
                    )
                )
            }
        }
    }

    override suspend fun runGeoLocation(target: String) {
        _networkState.update { state ->
            state.copy(
                geoLocateState = GeoLocateState(
                    status = GeoLocateStatus.RUNNING,
                    target = target
                )
            )
        }
        try {
            val result = NetworkManager.fetchIpGeolocate(target)
            _networkState.update { state ->
                state.copy(
                    geoLocateState = GeoLocateState(
                        status = GeoLocateStatus.FINISHED,
                        target = target,
                        result = result
                    )
                )
            }
        } catch (e: Exception) {
            _networkState.update { state ->
                state.copy(
                    geoLocateState = GeoLocateState(
                        status = GeoLocateStatus.ERROR,
                        target = target,
                        errorMessage = e.message ?: "Geolocate search failed"
                    )
                )
            }
        }
    }
}
