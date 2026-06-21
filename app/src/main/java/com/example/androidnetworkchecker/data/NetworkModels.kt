package com.example.androidnetworkchecker.data

enum class InterfaceType {
    WIFI,
    MOBILE,
    VPN,
    LOOPBACK,
    OTHER
}

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>,
    val type: InterfaceType,
    val prefixLength: Int = 24
)

enum class ConnectionType {
    WIFI,
    MOBILE,
    VPN,
    OTHER,
    NONE
}

data class ActiveConnectionInfo(
    val type: ConnectionType,
    val interfaceName: String?,
    val ipAddresses: List<String>,
    val prefixLength: Int = 24
)

data class DnsResolverInfo(
    val ip: String,
    val country: String?,
    val countryName: String?,
    val asn: String?
)

data class DnsLeakTestResult(
    val publicIp: String?,
    val publicIpCountry: String?,
    val publicIpAsn: String?,
    val dnsServers: List<DnsResolverInfo>,
    val conclusion: String
)

enum class ScanStatus {
    IDLE,
    SCANNING,
    FINISHED,
    ERROR
}

data class ScannedHost(
    val ip: String,
    val hostname: String?,
    val isReachable: Boolean
)

data class ScanState(
    val status: ScanStatus = ScanStatus.IDLE,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val activeHosts: List<ScannedHost> = emptyList(),
    val errorMessage: String? = null,
    val targetInterface: String? = null,
    val targetSubnet: String? = null
)

// Dashboard overview data class
data class QuickIps(
    val publicIp: String = "Fetching...",
    val publicCountry: String = "",
    val publicCountryCode: String = "",
    val publicAsn: String = "",
    val wifiIp: String = "Not Connected",
    val mobileIp: String = "Not Connected",
    val vpnIp: String = "Not Connected",
    val isFetchingPublic: Boolean = false
)

enum class PortScanStatus {
    IDLE,
    SCANNING,
    FINISHED,
    ERROR
}

data class ScannedPort(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean,
    val banner: String? = null
)

data class PortScanState(
    val status: PortScanStatus = PortScanStatus.IDLE,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val openPorts: List<ScannedPort> = emptyList(),
    val errorMessage: String? = null,
    val targetIp: String? = null
)

enum class TracerouteStatus {
    IDLE,
    RUNNING,
    FINISHED,
    ERROR
}

data class TracerouteHop(
    val hopNumber: Int,
    val ip: String,
    val hostname: String?,
    val latencyMs: Long,
    val countryCode: String?,
    val isResponsive: Boolean
)

data class TracerouteState(
    val status: TracerouteStatus = TracerouteStatus.IDLE,
    val target: String = "",
    val hops: List<TracerouteHop> = emptyList(),
    val scannedHops: Int = 0,
    val totalHops: Int = 30,
    val errorMessage: String? = null
)

enum class PingJitterStatus {
    IDLE,
    RUNNING,
    FINISHED,
    ERROR
}

data class PingLatency(
    val latencyMs: Double?,
    val timestamp: Long
)

data class PingJitterState(
    val status: PingJitterStatus = PingJitterStatus.IDLE,
    val target: String = "",
    val latencies: List<PingLatency> = emptyList(),
    val minMs: Double = 0.0,
    val maxMs: Double = 0.0,
    val avgMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val packetLossPercent: Double = 0.0,
    val errorMessage: String? = null
)

data class GuardLogEntry(
    val timestamp: String,
    val message: String,
    val type: String // "INFO", "WARNING", "ALERT"
)

data class GuardState(
    val vpnWatchdogEnabled: Boolean = false,
    val wifiIntruderEnabled: Boolean = false,
    val vpnStatus: String = "Inactive",
    val wifiStatus: String = "Inactive",
    val logs: List<GuardLogEntry> = emptyList()
)

data class WifiSignalState(
    val rssi: Int = 0,
    val linkSpeedMbps: Int = 0,
    val ssid: String = "Unknown SSID",
    val frequencyMhz: Int = 0,
    val signalLevel: Int = 0, // 0 to 4
    val qualityText: String = "Disconnected",
    val isConnected: Boolean = false
)

enum class DnsBenchmarkStatus {
    IDLE, RUNNING, FINISHED, ERROR
}

data class DnsBenchmarkServer(
    val name: String,
    val ip: String,
    val latencyMs: Long?,
    val isResponsive: Boolean = false
)

data class DnsBenchmarkState(
    val status: DnsBenchmarkStatus = DnsBenchmarkStatus.IDLE,
    val targetDomain: String = "google.com",
    val servers: List<DnsBenchmarkServer> = emptyList(),
    val errorMessage: String? = null
)

enum class GeoLocateStatus {
    IDLE, RUNNING, FINISHED, ERROR
}

data class GeoLocateResult(
    val ip: String = "",
    val country: String = "",
    val countryCode: String = "",
    val city: String = "",
    val region: String = "",
    val isp: String = "",
    val timezone: String = "",
    val zip: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val whoisData: String = ""
)

data class GeoLocateState(
    val status: GeoLocateStatus = GeoLocateStatus.IDLE,
    val target: String = "",
    val result: GeoLocateResult? = null,
    val errorMessage: String? = null
)

data class NetworkState(
    val interfaces: List<NetworkInterfaceInfo> = emptyList(),
    val activeConnection: ActiveConnectionInfo = ActiveConnectionInfo(ConnectionType.NONE, null, emptyList()),
    val dnsLeakTestResult: DnsLeakTestResult? = null,
    val isTestingDns: Boolean = false,
    val dnsTestProgress: String? = null,
    val scanState: ScanState = ScanState(),
    val quickIps: QuickIps = QuickIps(),
    val portScanState: PortScanState = PortScanState(),
    val tracerouteState: TracerouteState = TracerouteState(),
    val pingJitterState: PingJitterState = PingJitterState(),
    val guardState: GuardState = GuardState(),
    val wifiSignalState: WifiSignalState = WifiSignalState(),
    val dnsBenchmarkState: DnsBenchmarkState = DnsBenchmarkState(),
    val geoLocateState: GeoLocateState = GeoLocateState()
)

// Physical security scanner models
data class EmfScannerState(
    val currentUt: Float = 0f,
    val baselineUt: Float = 45f,
    val history: List<Float> = emptyList(),
    val isCalibrated: Boolean = false,
    val isAlertActive: Boolean = false
)

enum class SpyMode { GLINT_STROBE, IR_THERMAL, OFF }

data class SpyScannerState(
    val mode: SpyMode = SpyMode.OFF,
    val isStrobeActive: Boolean = false,
    val selectedCameraId: String = "0" // 0 = Rear, 1 = Front
)

