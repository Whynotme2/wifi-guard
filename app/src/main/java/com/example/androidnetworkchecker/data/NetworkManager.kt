package com.example.androidnetworkchecker.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.Network
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.channels.Channel

object NetworkManager {

    fun getNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val result = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (networkInterface in interfaces.iterator()) {
                val name = networkInterface.name
                val displayName = networkInterface.displayName
                val ipv4List = mutableListOf<String>()
                val ipv6List = mutableListOf<String>()

                val addresses = networkInterface.inetAddresses
                for (address in addresses.iterator()) {
                    val hostAddress = address.hostAddress ?: continue
                    val cleanAddress = if (hostAddress.contains("%")) {
                        hostAddress.substringBefore("%")
                    } else {
                        hostAddress
                    }

                    if (address is Inet4Address) {
                        ipv4List.add(cleanAddress)
                    } else if (address is Inet6Address) {
                        ipv6List.add(cleanAddress)
                    }
                }

                var prefixLength = 24
                try {
                    val interfaceAddresses = networkInterface.interfaceAddresses
                    for (ifAddr in interfaceAddresses) {
                        if (ifAddr.address is Inet4Address) {
                            prefixLength = ifAddr.networkPrefixLength.toInt()
                            break
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val type = when {
                    networkInterface.isLoopback -> InterfaceType.LOOPBACK
                    name.startsWith("wlan", ignoreCase = true) || 
                    name.startsWith("ap", ignoreCase = true) || 
                    name.startsWith("p2p", ignoreCase = true) -> InterfaceType.WIFI
                    name.startsWith("rmnet", ignoreCase = true) || 
                    name.startsWith("pdp", ignoreCase = true) || 
                    name.startsWith("ccmni", ignoreCase = true) || 
                    name.startsWith("epdg", ignoreCase = true) ||
                    name.startsWith("ppp", ignoreCase = true) && !name.contains("vpn") -> InterfaceType.MOBILE
                    name.startsWith("tun", ignoreCase = true) || 
                    name.startsWith("tap", ignoreCase = true) || 
                    name.startsWith("ppp", ignoreCase = true) || 
                    name.contains("vpn", ignoreCase = true) || 
                    name.contains("ipsec", ignoreCase = true) -> InterfaceType.VPN
                    else -> InterfaceType.OTHER
                }

                if (ipv4List.isNotEmpty() || ipv6List.isNotEmpty()) {
                    result.add(
                        NetworkInterfaceInfo(
                            name = name,
                            displayName = displayName,
                            ipv4Addresses = ipv4List,
                            ipv6Addresses = ipv6List,
                            type = type,
                            prefixLength = prefixLength
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.sortedWith(compareBy({ it.type.ordinal }, { it.name }))
    }

    fun getActiveConnectionInfo(context: Context): ActiveConnectionInfo {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return ActiveConnectionInfo(ConnectionType.NONE, null, emptyList())
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return ActiveConnectionInfo(ConnectionType.NONE, null, emptyList())
            val linkProperties = cm.getLinkProperties(activeNetwork)

            val type = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE
                else -> ConnectionType.OTHER
            }

            var prefixLength = 24
            val linkAddresses = linkProperties?.linkAddresses
            if (linkAddresses != null) {
                for (la in linkAddresses) {
                    if (la.address is Inet4Address) {
                        prefixLength = la.prefixLength
                        break
                    }
                }
            }

            val interfaceName = linkProperties?.interfaceName
            val ipAddresses = linkProperties?.linkAddresses?.map { 
                val host = it.address.hostAddress ?: ""
                if (host.contains("%")) host.substringBefore("%") else host
            } ?: emptyList()

            return ActiveConnectionInfo(
                type = type,
                interfaceName = interfaceName,
                ipAddresses = ipAddresses,
                prefixLength = prefixLength
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return ActiveConnectionInfo(ConnectionType.NONE, null, emptyList())
        }
    }

    private fun isValidIp(ip: String): Boolean {
        val ipv4Regex = """^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""".toRegex()
        val ipv6Regex = """^[0-9a-fA-F:]+$""".toRegex()
        return ipv4Regex.matches(ip) || (ipv6Regex.matches(ip) && ip.contains(":"))
    }

    fun getNetworkForInterface(context: Context, interfaceName: String): Network? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networks = cm.allNetworks
            for (net in networks) {
                val lp = cm.getLinkProperties(net)
                if (lp?.interfaceName == interfaceName) {
                    return net
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun fetchPublicIpDetails(): Map<String, String> {
        val details = mutableMapOf<String, String>()
        
        val providers = listOf(
            Pair("https://api.ip.sb/geoip") { json: String ->
                val obj = JSONObject(json)
                mapOf(
                    "ip" to obj.optString("ip"),
                    "country" to obj.optString("country"),
                    "countryCode" to obj.optString("country_code"),
                    "asn" to obj.optString("organization")
                )
            },
            Pair("https://ipapi.co/json/") { json: String ->
                val obj = JSONObject(json)
                mapOf(
                    "ip" to obj.optString("ip"),
                    "country" to obj.optString("country_name"),
                    "countryCode" to obj.optString("country"),
                    "asn" to obj.optString("org")
                )
            },
            Pair("https://ipinfo.io/json") { json: String ->
                val obj = JSONObject(json)
                mapOf(
                    "ip" to obj.optString("ip"),
                    "country" to obj.optString("country"),
                    "countryCode" to obj.optString("country"),
                    "asn" to obj.optString("org")
                )
            },
            Pair("https://api.ipify.org?format=json") { json: String ->
                val obj = JSONObject(json)
                mapOf("ip" to obj.optString("ip"))
            },
            Pair("https://icanhazip.com") { text: String ->
                val ip = text.trim()
                if (isValidIp(ip)) mapOf("ip" to ip) else emptyMap()
            },
            Pair("https://ifconfig.me/ip") { text: String ->
                val ip = text.trim()
                if (isValidIp(ip)) mapOf("ip" to ip) else emptyMap()
            }
        )

        for ((url, parser) in providers) {
            val response = fetchUrl(url)
            if (response != null && response.isNotEmpty()) {
                try {
                    val parsed = parser(response)
                    val ip = parsed["ip"] ?: ""
                    if (ip.isNotEmpty()) {
                        details["ip"] = ip
                        details["country"] = parsed["country"] ?: ""
                        details["countryCode"] = parsed["countryCode"] ?: ""
                        details["asn"] = parsed["asn"] ?: ""
                        return details
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return details
    }

    fun getSubnetIps(localIp: String, prefixLength: Int): List<String> {
        try {
            val inet = InetAddress.getByName(localIp)
            if (inet !is Inet4Address) return emptyList()

            val bytes = inet.address
            val mask = if (prefixLength <= 0 || prefixLength > 32) 24 else prefixLength
            
            if (mask < 16) {
                val prefix = localIp.substringBeforeLast(".")
                return (1..254).map { "$prefix.$it" }
            }

            var ipInt = 0
            for (b in bytes) {
                ipInt = (ipInt shl 8) or (b.toInt() and 0xFF)
            }

            val maskInt = if (mask == 32) -1 else (-1 shl (32 - mask))
            val networkInt = ipInt and maskInt
            val broadcastInt = networkInt or maskInt.inv()

            val start = networkInt + 1
            val end = broadcastInt - 1
            
            if (end - start > 512) {
                val prefix = localIp.substringBeforeLast(".")
                return (1..254).map { "$prefix.$it" }
            }

            val ips = mutableListOf<String>()
            for (i in start..end) {
                val ipBytes = byteArrayOf(
                    ((i shr 24) and 0xFF).toByte(),
                    ((i shr 16) and 0xFF).toByte(),
                    ((i shr 8) and 0xFF).toByte(),
                    (i and 0xFF).toByte()
                )
                val resolved = InetAddress.getByAddress(ipBytes).hostAddress ?: continue
                ips.add(resolved)
            }
            return ips
        } catch (e: Exception) {
            e.printStackTrace()
            val prefix = localIp.substringBeforeLast(".")
            return (1..254).map { "$prefix.$it" }
        }
    }

    fun pingHost(ip: String, interfaceName: String? = null): Boolean {
        if (interfaceName != null) {
            try {
                val process = Runtime.getRuntime().exec("ping -c 1 -w 1 -I $interfaceName $ip")
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                var line: String?
                var success = false
                while (reader.readLine().also { line = it } != null) {
                    if ((line!!.contains("bytes from", ignoreCase = true) || line!!.contains("ttl=", ignoreCase = true)) && line!!.contains("time=", ignoreCase = true)) {
                        success = true
                        break
                    }
                }
                process.destroy()
                if (success) return true
            } catch (e: Exception) {
                // fallback
            }
        }
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -w 1 $ip")
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            var line: String?
            var success = false
            while (reader.readLine().also { line = it } != null) {
                if ((line!!.contains("bytes from", ignoreCase = true) || line!!.contains("ttl=", ignoreCase = true)) && line!!.contains("time=", ignoreCase = true)) {
                    success = true
                    break
                }
            }
            process.destroy()
            success
        } catch (e: Exception) {
            false
        }
    }

    private fun queryNetBiosName(ipAddress: String): String? {
        var socket: java.net.DatagramSocket? = null
        try {
            socket = java.net.DatagramSocket()
            socket.soTimeout = 200 // 200ms timeout
            
            // NetBIOS Node Status Request Payload
            val payload = byteArrayOf(
                0xA2.toByte(), 0x48.toByte(), // Transaction ID
                0x00.toByte(), 0x00.toByte(), // Flags (Query, Standard)
                0x00.toByte(), 0x01.toByte(), // Questions: 1
                0x00.toByte(), 0x00.toByte(), // Answer RRs: 0
                0x00.toByte(), 0x00.toByte(), // Authority RRs: 0
                0x00.toByte(), 0x00.toByte(), // Additional RRs: 0
                // Name: encoded "*" representation
                0x20.toByte(),
                0x43.toByte(), 0x4B.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x00.toByte(),
                0x00.toByte(), 0x21.toByte(), // Type: NBSTAT (0x0021)
                0x00.toByte(), 0x01.toByte()  // Class: IN (0x0001)
            )
            
            val address = java.net.InetAddress.getByName(ipAddress)
            val packet = java.net.DatagramPacket(payload, payload.size, address, 137)
            socket.send(packet)
            
            val buffer = ByteArray(1024)
            val receivePacket = java.net.DatagramPacket(buffer, buffer.size)
            socket.receive(receivePacket)
            
            val len = receivePacket.length
            if (len > 56) {
                val numNames = buffer[56].toInt() and 0xFF
                var offset = 57
                for (i in 0 until numNames) {
                    if (offset + 18 > len) break
                    val nameBytes = ByteArray(15)
                    System.arraycopy(buffer, offset, nameBytes, 0, 15)
                    val type = buffer[offset + 15].toInt() and 0xFF
                    val flags = ((buffer[offset + 16].toInt() and 0xFF) shl 8) or (buffer[offset + 17].toInt() and 0xFF)
                    
                    val name = String(nameBytes, Charsets.US_ASCII).trim()
                    if (type == 0x00 && (flags and 0x8000) == 0 && name.isNotEmpty()) {
                        return name
                    }
                    offset += 18
                }
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            socket?.close()
        }
        return null
    }

    suspend fun scanHosts(
        context: Context,
        interfaceName: String,
        ips: List<String>,
        progressCallback: (scanned: Int, total: Int, foundHost: ScannedHost?) -> Unit
    ) {
        val total = ips.size
        var scannedCount = 0
        val countLock = Any()
        
        val checkPorts = intArrayOf(80, 443, 53, 22)

        coroutineScope {
            val semaphore = Channel<Unit>(40)

            val jobs = ips.map { ip ->
                launch(Dispatchers.IO) {
                    semaphore.send(Unit)
                    var active = false
                    try {
                        // 1. Try pinging first (fast path)
                        if (pingHost(ip, interfaceName)) {
                            active = true
                        } else {
                            // 2. Fallback to TCP port probe
                            for (port in checkPorts) {
                                var socket: java.net.Socket? = null
                                try {
                                    socket = java.net.Socket()
                                    val net = getNetworkForInterface(context, interfaceName)
                                    net?.bindSocket(socket)
                                    
                                    socket.connect(java.net.InetSocketAddress(ip, port), 200)
                                    active = true
                                    break
                                } catch (e: java.net.ConnectException) {
                                    val msg = e.message ?: ""
                                    if (msg.contains("refused", ignoreCase = true) || msg.contains("reset", ignoreCase = true)) {
                                        active = true
                                        break
                                    }
                                } catch (e: Exception) {
                                    // Timeout or other errors
                                } finally {
                                    try { socket?.close() } catch (ex: Exception) {}
                                }
                            }
                        }

                        if (active) {
                            val address = InetAddress.getByName(ip)
                            var hostname = address.canonicalHostName.takeIf { it != ip }
                            if (hostname == null) {
                                hostname = queryNetBiosName(ip)
                            }
                            val host = ScannedHost(ip, hostname, true)
                            synchronized(countLock) {
                                scannedCount++
                                progressCallback(scannedCount, total, host)
                            }
                        } else {
                            synchronized(countLock) {
                                scannedCount++
                                progressCallback(scannedCount, total, null)
                            }
                        }
                    } catch (e: Exception) {
                        synchronized(countLock) {
                            scannedCount++
                            progressCallback(scannedCount, total, null)
                        }
                    } finally {
                        semaphore.receive()
                    }
                }
            }
            jobs.joinAll()
        }
    }

    fun grabPortBanner(ip: String, port: Int): String? {
        var socket: java.net.Socket? = null
        var reader: java.io.BufferedReader? = null
        return try {
            socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(ip, port), 1000)
            socket.soTimeout = 1500
            
            if (port == 80 || port == 8080) {
                val writer = java.io.PrintWriter(socket.getOutputStream(), true)
                writer.print("HEAD / HTTP/1.1\r\nHost: $ip\r\nConnection: close\r\n\r\n")
                writer.flush()
            } else if (port == 443 || port == 8443) {
                val sslSocketFactory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
                val sslSocket = sslSocketFactory.createSocket(socket, ip, port, true) as javax.net.ssl.SSLSocket
                sslSocket.startHandshake()
                val writer = java.io.PrintWriter(sslSocket.getOutputStream(), true)
                writer.print("HEAD / HTTP/1.1\r\nHost: $ip\r\nConnection: close\r\n\r\n")
                writer.flush()
                val sslReader = java.io.BufferedReader(java.io.InputStreamReader(sslSocket.getInputStream()))
                val firstLine = sslReader.readLine()
                sslSocket.close()
                return firstLine?.trim()?.take(50)
            }
            
            reader = java.io.BufferedReader(java.io.InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine()
            firstLine?.trim()?.take(50)
        } catch (e: Exception) {
            null
        } finally {
            try { reader?.close() } catch (e: Exception) {}
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    suspend fun scanPorts(
        ip: String,
        ports: List<Int>,
        progressCallback: (scanned: Int, total: Int, foundPort: ScannedPort?) -> Unit
    ) {
        val total = ports.size
        var scannedCount = 0
        val countLock = Any()

        coroutineScope {
            val semaphore = Channel<Unit>(30)

            val jobs = ports.map { port ->
                launch(Dispatchers.IO) {
                    semaphore.send(Unit)
                    var open = false
                    var banner: String? = null
                    var socket: java.net.Socket? = null
                    try {
                        socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress(ip, port), 250)
                        open = true
                        banner = grabPortBanner(ip, port)
                    } catch (e: java.net.ConnectException) {
                        val msg = e.message ?: ""
                        if (msg.contains("refused", ignoreCase = true) || msg.contains("reset", ignoreCase = true)) {
                            // Closed port
                        }
                    } catch (e: Exception) {
                        // Timeout
                    } finally {
                        try { socket?.close() } catch (ex: Exception) {}
                    }

                    synchronized(countLock) {
                        scannedCount++
                        val p = if (open) ScannedPort(port, getPortServiceName(port), true, banner) else null
                        progressCallback(scannedCount, total, p)
                    }
                    semaphore.receive()
                }
            }
            jobs.joinAll()
        }
    }

    fun runSinglePing(target: String): Double? {
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -w 2 $target")
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            var line: String?
            var latency: Double? = null
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains("time=")) {
                    val part = line!!.substringAfter("time=")
                    val timeStr = part.substringBefore(" ms").substringBefore("ms").trim()
                    latency = timeStr.toDoubleOrNull()
                    break
                }
            }
            process.waitFor()
            latency
        } catch (e: Exception) {
            null
        }
    }

    suspend fun runTraceroute(
        target: String,
        maxHops: Int = 30,
        progressCallback: (hop: TracerouteHop) -> Unit
    ) {
        coroutineScope {
            for (hop in 1..maxHops) {
                val startTime = System.currentTimeMillis()
                var ip: String? = null
                var responsive = false
                
                try {
                    val process = Runtime.getRuntime().exec("ping -c 1 -t $hop $target")
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val cleanLine = line!!.trim()
                        if (cleanLine.contains("Time to live exceeded", ignoreCase = true)) {
                            ip = extractIpAddress(cleanLine)
                            responsive = true
                            break
                        } else if (cleanLine.contains("bytes from", ignoreCase = true)) {
                            ip = extractIpAddress(cleanLine)
                            responsive = true
                            break
                        }
                    }
                    process.waitFor()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                val endTime = System.currentTimeMillis()
                val rtt = endTime - startTime
                
                val resolvedIp = ip ?: ""
                var hostname: String? = null
                if (responsive && resolvedIp.isNotEmpty()) {
                    try {
                        val addr = InetAddress.getByName(resolvedIp)
                        hostname = addr.canonicalHostName.takeIf { it != resolvedIp }
                    } catch (e: Exception) {
                        // ignore dns resolve
                    }
                }
                
                val resultHop = TracerouteHop(
                    hopNumber = hop,
                    ip = resolvedIp,
                    hostname = hostname,
                    latencyMs = if (responsive) rtt else 0L,
                    countryCode = null,
                    isResponsive = responsive
                )
                
                progressCallback(resultHop)
                
                if (responsive && resolvedIp.isNotEmpty()) {
                    try {
                        val targetAddr = InetAddress.getByName(target).hostAddress
                        if (resolvedIp == targetAddr || resolvedIp == target) {
                            break
                        }
                    } catch (e: Exception) {
                        if (resolvedIp == target) break
                    }
                }
                
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun extractIpAddress(line: String): String? {
        val ipv4Regex = "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b".toRegex()
        val ipv4Match = ipv4Regex.find(line)
        if (ipv4Match != null) return ipv4Match.value
        
        val ipv6Regex = "\\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\b|\\b((?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)::((?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)\\b".toRegex()
        val ipv6Match = ipv6Regex.find(line)
        return ipv6Match?.value
    }

    fun getPortServiceName(port: Int): String {
        return when (port) {
            21 -> "FTP"
            22 -> "SSH"
            23 -> "Telnet"
            25 -> "SMTP"
            53 -> "DNS"
            80 -> "HTTP"
            110 -> "POP3"
            135 -> "RPC"
            139 -> "NetBIOS"
            143 -> "IMAP"
            443 -> "HTTPS"
            445 -> "SMB"
            1433 -> "MSSQL"
            3306 -> "MySQL"
            3389 -> "RDP"
            5900 -> "VNC"
            8080 -> "HTTP-ALT"
            8443 -> "HTTPS-ALT"
            else -> "Unknown"
        }
    }

    fun runDnsLeakTest(progressCallback: (String) -> Unit): DnsLeakTestResult {
        progressCallback("Contacting leak test server...")
        val testId = fetchUrl("https://bash.ws/id")?.trim() 
            ?: throw Exception("Failed to get unique test ID from bash.ws")

        progressCallback("Test ID generated: $testId\nResolving test domains...")

        // Resolve 10 domains to force DNS lookups
        for (i in 1..10) {
            progressCallback("Resolving domain $i of 10...")
            try {
                InetAddress.getAllByName("$i.$testId.bash.ws")
            } catch (e: Exception) {
                // Ignore resolution failures
            }
            Thread.sleep(150)
        }

        progressCallback("Querying leak test results...")
        Thread.sleep(500)

        val jsonResponse = fetchUrl("https://bash.ws/dnsleak/test/$testId?json")
            ?: throw Exception("Failed to retrieve test results from bash.ws")

        var publicIp: String? = null
        var country: String? = null
        var asn: String? = null
        val dnsServers = mutableListOf<DnsResolverInfo>()
        var conclusion = "Unknown"

        try {
            val jsonArray = JSONArray(jsonResponse)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val type = obj.optString("type")
                val ip = obj.optString("ip")
                val countryCode = obj.optString("country").takeIf { it.isNotEmpty() }
                val countryName = obj.optString("country_name").takeIf { it.isNotEmpty() }
                val asnName = obj.optString("asn").takeIf { it.isNotEmpty() }

                when (type) {
                    "ip" -> {
                        publicIp = ip
                        country = countryName
                        asn = asnName
                    }
                    "dns" -> {
                        dnsServers.add(
                            DnsResolverInfo(
                                ip = ip,
                                country = countryCode,
                                countryName = countryName,
                                asn = asnName
                            )
                        )
                    }
                    "conclusion" -> {
                        conclusion = ip
                    }
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse response: ${e.message}")
        }

        return DnsLeakTestResult(
            publicIp = publicIp,
            publicIpCountry = country,
            publicIpAsn = asn,
            dnsServers = dnsServers,
            conclusion = conclusion
        )
    }

    private fun fetchUrl(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line).append("\n")
                }
                reader.close()
                response.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun getWifiSignalInfo(context: Context): WifiSignalState {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            var isConnected = false
            
            val networks = cm.allNetworks
            for (net in networks) {
                val caps = cm.getNetworkCapabilities(net)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    isConnected = true
                    break
                }
            }
            
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val wifiInfo = wifiManager.connectionInfo
            
            if (!isConnected && wifiInfo != null && wifiInfo.networkId != -1) {
                isConnected = true
            }

            if (!isConnected) {
                return WifiSignalState()
            }
            
            val rssi = wifiInfo?.rssi ?: -127
            val linkSpeed = wifiInfo?.linkSpeed ?: 0
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "Connected"
            val frequency = wifiInfo?.frequency ?: 0
            
            val level = when {
                rssi <= -100 -> 0
                rssi >= -55 -> 4
                else -> {
                    val range = -55 - (-100)
                    val portion = rssi - (-100)
                    (portion * 4 / range)
                }
            }
            val qualityText = when (level) {
                4 -> "Excellent"
                3 -> "Good"
                2 -> "Fair"
                1 -> "Poor"
                else -> "Weak/No Signal"
            }

            return WifiSignalState(
                rssi = rssi,
                linkSpeedMbps = linkSpeed,
                ssid = if (ssid == "<unknown ssid>" || ssid == "0x") "Connected" else ssid,
                frequencyMhz = frequency,
                signalLevel = level,
                qualityText = qualityText,
                isConnected = true
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return WifiSignalState()
        }
    }

    fun sendWakeOnLan(macAddress: String, broadcastIp: String = "255.255.255.255") {
        try {
            val cleanMac = macAddress.replace(":", "").replace("-", "")
            if (cleanMac.length != 12) return
            val macBytes = ByteArray(6)
            for (i in 0..5) {
                macBytes[i] = cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            
            val bytes = ByteArray(6 + 16 * macBytes.size)
            for (i in 0..5) {
                bytes[i] = 0xff.toByte()
            }
            for (i in 6 until bytes.size step macBytes.size) {
                System.arraycopy(macBytes, 0, bytes, i, macBytes.size)
            }
            
            val address = InetAddress.getByName(broadcastIp)
            val packet = java.net.DatagramPacket(bytes, bytes.size, address, 9)
            val socket = java.net.DatagramSocket()
            socket.broadcast = true
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun queryDnsServer(dnsIp: String, domain: String): Long {
        val startTime = System.currentTimeMillis()
        var socket: java.net.DatagramSocket? = null
        try {
            socket = java.net.DatagramSocket()
            socket.soTimeout = 1200
            
            val out = java.io.ByteArrayOutputStream()
            // Transaction ID (2 bytes)
            out.write(0x12)
            out.write(0x34)
            // Flags: Standard query (2 bytes)
            out.write(0x01)
            out.write(0x00)
            // Questions count: 1 (2 bytes)
            out.write(0x00)
            out.write(0x01)
            // Answer RRs, Authority RRs, Additional RRs: 0 (6 bytes)
            out.write(0x00)
            out.write(0x00)
            out.write(0x00)
            out.write(0x00)
            out.write(0x00)
            out.write(0x00)
            
            // Question Section
            val parts = domain.split(".")
            for (part in parts) {
                val bytes = part.toByteArray(Charsets.US_ASCII)
                out.write(bytes.size)
                out.write(bytes)
            }
            out.write(0x00)
            
            // QTYPE: A (0x0001)
            out.write(0x00)
            out.write(0x01)
            // QCLASS: IN (0x0001)
            out.write(0x00)
            out.write(0x01)
            
            val query = out.toByteArray()
            val address = InetAddress.getByName(dnsIp)
            val packet = java.net.DatagramPacket(query, query.size, address, 53)
            socket.send(packet)
            
            val response = ByteArray(512)
            val receivePacket = java.net.DatagramPacket(response, response.size)
            socket.receive(receivePacket)
            
            return System.currentTimeMillis() - startTime
        } catch (e: Exception) {
            return -1L
        } finally {
            socket?.close()
        }
    }

    fun fetchIpGeolocate(target: String): GeoLocateResult {
        var connection: HttpURLConnection? = null
        try {
            val urlStr = if (target.isEmpty()) {
                "https://ipapi.co/json/"
            } else {
                val cleanTarget = target.trim().replace("/", "").replace("http:", "").replace("https:", "")
                "https://ipapi.co/$cleanTarget/json/"
            }
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val json = JSONObject(response.toString())
                if (json.has("error") && json.getBoolean("error")) {
                    throw Exception(json.optString("reason", "API Error"))
                }
                
                val ip = json.optString("ip", "")
                val country = json.optString("country_name", "")
                val countryCode = json.optString("country_code", "")
                val city = json.optString("city", "")
                val region = json.optString("region", "")
                val org = json.optString("org", "")
                val timezone = json.optString("timezone", "")
                val zip = json.optString("postal", "")
                val lat = json.optDouble("latitude", 0.0)
                val lon = json.optDouble("longitude", 0.0)
                
                val whoisBuilder = StringBuilder()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    whoisBuilder.append(key.uppercase().replace("_", " ")).append(": ").append(json.get(key).toString()).append("\n")
                }
                
                return GeoLocateResult(
                    ip = ip,
                    country = country,
                    countryCode = countryCode,
                    city = city,
                    region = region,
                    isp = org,
                    timezone = timezone,
                    zip = zip,
                    lat = lat,
                    lon = lon,
                    whoisData = whoisBuilder.toString()
                )
            } else {
                throw Exception("HTTP Error $responseCode")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return GeoLocateResult(
                ip = target,
                country = "Error",
                isp = e.message ?: "Failed to geolocate"
            )
        } finally {
            connection?.disconnect()
        }
    }

    fun getDnsServers(context: Context): List<String> {
        val servers = mutableListOf<String>()
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                val linkProperties = cm.getLinkProperties(activeNetwork)
                if (linkProperties != null) {
                    for (dns in linkProperties.dnsServers) {
                        val host = dns.hostAddress
                        if (host != null) {
                            servers.add(host)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (servers.isEmpty()) {
            servers.add("8.8.8.8")
        }
        return servers
    }
}
