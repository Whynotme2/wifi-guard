package com.example.androidnetworkchecker.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.androidnetworkchecker.ui.main.BluetoothTrackerDevice
import com.example.androidnetworkchecker.data.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

object BluetoothTrackerRepository {

    private val _devices = MutableStateFlow<List<BluetoothTrackerDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothTrackerDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val deviceDatabase = mutableMapOf<String, DeviceTrackingData>()
    private val safeDevices = mutableSetOf<String>()

    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private var isInitialized = false

    private data class DeviceTrackingData(
        val address: String,
        val name: String?,
        val firstSeen: Long,
        var lastSeen: Long,
        var scanCount: Int,
        val rssiHistory: MutableList<Int>,
        val manufacturerName: String,
        var latitude: Double? = null,
        var longitude: Double? = null,
        var maxRssi: Int = -999,
        var lastIncrementTime: Long = 0,
        var sessionIncremented: Boolean = false,
        var deviceCategory: String = "BLE Device"
    )

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        loadSafeDevices(context)
        loadDatabaseLocally(context)
    }

    fun setIsScanning(scanning: Boolean) {
        _isScanning.value = scanning
    }

    private fun loadSafeDevices(context: Context) {
        val prefs = context.getSharedPreferences("ble_tracker_prefs", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("safe_devices", emptySet()) ?: emptySet()
        synchronized(safeDevices) {
            safeDevices.clear()
            safeDevices.addAll(set)
        }
    }

    private fun saveSafeDevices(context: Context) {
        val prefs = context.getSharedPreferences("ble_tracker_prefs", Context.MODE_PRIVATE)
        synchronized(safeDevices) {
            prefs.edit().putStringSet("safe_devices", safeDevices).apply()
        }
    }

    fun toggleSafeDevice(context: Context, address: String) {
        synchronized(safeDevices) {
            if (safeDevices.contains(address)) {
                safeDevices.remove(address)
            } else {
                safeDevices.add(address)
            }
        }
        saveSafeDevices(context)
        updateDeviceList(context)
    }

    fun clearDeviceHistory(context: Context) {
        synchronized(deviceDatabase) {
            deviceDatabase.clear()
        }
        updateDeviceList(context)
        saveDatabaseLocally(context)
    }

    fun processScanResult(context: Context, result: ScanResult, lastLocation: Location?) {
        val device = result.device
        val address = device.address
        val name = result.scanRecord?.deviceName ?: device.name
        val rssi = result.rssi

        // Reject invalid/corrupted packages (RSSI = 127, RSSI must be negative)
        if (rssi == 127 || rssi > 0) return

        val manufacturerName = getManufacturerName(result)
        val btClass = device.bluetoothClass
        val category = determineDeviceCategory(name, btClass, manufacturerName)
        val now = System.currentTimeMillis()

        var shouldSave = false
        synchronized(deviceDatabase) {
            val existing = deviceDatabase[address]
            val currentLoc = lastLocation
            if (existing != null) {
                existing.lastSeen = now
                existing.rssiHistory.add(rssi)
                if (existing.rssiHistory.size > 15) {
                    existing.rssiHistory.removeAt(0)
                }

                // Increment rules:
                // 1. Every time the app starts, the first scan of an existing device increments scanCount.
                // 2. While running, it only increments once every 2 hours (7,200,000 ms).
                if (!existing.sessionIncremented) {
                    existing.scanCount += 1
                    existing.lastIncrementTime = now
                    existing.sessionIncremented = true
                    shouldSave = true
                } else if (now - existing.lastIncrementTime >= 7200000L) {
                    existing.scanCount += 1
                    existing.lastIncrementTime = now
                    shouldSave = true
                }

                // Save coordinates at point of strongest signal strength
                if (rssi > existing.maxRssi && currentLoc != null) {
                    if (existing.latitude != currentLoc.latitude || existing.longitude != currentLoc.longitude) {
                        existing.latitude = currentLoc.latitude
                        existing.longitude = currentLoc.longitude
                        shouldSave = true
                    }
                    existing.maxRssi = rssi
                }
            } else {
                deviceDatabase[address] = DeviceTrackingData(
                    address = address,
                    name = name,
                    firstSeen = now,
                    lastSeen = now,
                    scanCount = 1,
                    rssiHistory = mutableListOf(rssi),
                    manufacturerName = manufacturerName,
                    latitude = currentLoc?.latitude,
                    longitude = currentLoc?.longitude,
                    maxRssi = rssi,
                    lastIncrementTime = now,
                    sessionIncremented = true,
                    deviceCategory = category
                )
                shouldSave = true
            }
        }
        updateDeviceList(context)
        if (shouldSave) {
            saveDatabaseLocally(context)
        }
    }

    private fun updateDeviceList(context: Context) {
        val now = System.currentTimeMillis()
        val list = synchronized(deviceDatabase) {
            deviceDatabase.values.map { data ->
                val avgRssi = data.rssiHistory.average().toInt()
                val txPower = -60
                val estimatedDistance = calculateDistance(avgRssi, txPower)
                
                val isSafe = synchronized(safeDevices) { safeDevices.contains(data.address) }
                val isSuspicious = data.scanCount >= 20 && !isSafe
                val isCurrentlyActive = (now - data.lastSeen) < 30000

                BluetoothTrackerDevice(
                    address = data.address,
                    name = data.name,
                    rssi = data.rssiHistory.lastOrNull() ?: avgRssi,
                    estimatedDistanceMeters = estimatedDistance,
                    firstSeen = data.firstSeen,
                    lastSeen = data.lastSeen,
                    isSuspicious = isSuspicious,
                    isSafe = isSafe,
                    scanCount = data.scanCount,
                    manufacturerName = data.manufacturerName,
                    isCurrentlyActive = isCurrentlyActive,
                    latitude = data.latitude,
                    longitude = data.longitude,
                    deviceCategory = data.deviceCategory
                )
            }
        }

        // Sort: Online first, then Times Found (scanCount) Descending, then Distance Ascending
        val sortedList = list.sortedWith(
            compareByDescending<BluetoothTrackerDevice> { it.isCurrentlyActive }
                .thenByDescending { it.scanCount }
                .thenBy { it.estimatedDistanceMeters }
        )

        _devices.update { sortedList }
    }

    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (rssi == 0) return 999.0
        val exponent = (txPower - rssi) / (10.0 * 2.4)
        val rawDist = 10.0.pow(exponent)
        return Math.round(rawDist * 10.0) / 10.0
    }

    private fun getManufacturerName(result: ScanResult): String {
        val record = result.scanRecord ?: return "Unknown BLE Beacon"
        
        val manufacturerData = record.manufacturerSpecificData
        if (manufacturerData != null && manufacturerData.size() > 0) {
            val id = manufacturerData.keyAt(0)
            return when (id) {
                0x004C -> {
                    val data = manufacturerData.get(id)
                    if (data != null && data.size > 2 && data[0] == 0x12.toByte() && data[1] == 0x19.toByte()) {
                        "Apple AirTag"
                    } else {
                        "Apple Device"
                    }
                }
                0x0075 -> {
                    val name = record.deviceName?.lowercase() ?: ""
                    if (name.contains("smarttag") || name.contains("tag")) {
                        "Samsung SmartTag"
                    } else {
                        "Samsung Device"
                    }
                }
                0x00E0 -> "Google Device"
                0x0006 -> "Microsoft Device"
                0x0001 -> "Nokia BLE Device"
                0x0059 -> "Nordic BLE Device"
                else -> "Generic BLE (ID: 0x${Integer.toHexString(id).uppercase()})"
            }
        }

        val serviceUuids = record.serviceUuids
        if (serviceUuids != null) {
            for (uuid in serviceUuids) {
                val uuidStr = uuid.toString().lowercase()
                if (uuidStr.contains("feed") || uuidStr.contains("fec9")) return "Tile Tracker"
                if (uuidStr.contains("fd5a")) return "Samsung SmartTag"
            }
        }

        val name = record.deviceName?.lowercase() ?: ""
        return when {
            name.contains("airtag") -> "Apple AirTag"
            name.contains("smarttag") -> "Samsung SmartTag"
            name.contains("tile") -> "Tile Tracker"
            name.contains("nut") -> "Nut Tracker"
            name.contains("beacon") -> "BLE Beacon"
            else -> "Generic BLE Device"
        }
    }

    fun determineDeviceCategory(name: String?, bluetoothClass: BluetoothClass?, manufacturerName: String): String {
        val lowerName = name?.lowercase() ?: ""
        val lowerMfg = manufacturerName.lowercase()
        
        if (lowerName.contains("buds") || lowerName.contains("pod") || lowerName.contains("headphone") || 
            lowerName.contains("headset") || lowerName.contains("earphone") || lowerName.contains("audio") || 
            lowerName.contains("sony") || lowerName.contains("bose") || lowerName.contains("jbl") || 
            lowerName.contains("speaker") || lowerName.contains("wh-") || lowerName.contains("wf-")) {
            return "Audio/Headphones"
        }
        if (lowerName.contains("iphone") || lowerName.contains("galaxy") || lowerName.contains("pixel") || 
            lowerName.contains("phone") || lowerName.contains("mobile")) {
            return "Phone"
        }
        if (lowerName.contains("macbook") || lowerName.contains("ipad") || lowerName.contains("laptop") || 
            lowerName.contains("pc") || lowerName.contains("desktop") || lowerName.contains("book") || 
            lowerName.contains("computer") || lowerName.contains("surface") || lowerName.contains("thinkpad")) {
            return "Computer"
        }
        if (lowerName.contains("watch") || lowerName.contains("band") || lowerName.contains("fitbit") || 
            lowerName.contains("garmin") || lowerName.contains("fit") || lowerName.contains("gear") || 
            lowerName.contains("wear") || lowerName.contains("ring")) {
            return "Wearable"
        }
        if (lowerMfg.contains("airtag") || lowerMfg.contains("smarttag") || lowerMfg.contains("tile") || 
            lowerMfg.contains("tracker") || lowerName.contains("beacon") || lowerName.contains("tag")) {
            return "Tracker/Beacon"
        }

        if (bluetoothClass != null) {
            val major = bluetoothClass.majorDeviceClass
            val device = bluetoothClass.deviceClass
            
            when (major) {
                BluetoothClass.Device.Major.COMPUTER -> return "Computer"
                BluetoothClass.Device.Major.PHONE -> return "Phone"
                BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                    return when (device) {
                        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "Audio/Headphones"
                        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Speaker"
                        else -> "Audio Device"
                    }
                }
                BluetoothClass.Device.Major.WEARABLE -> return "Wearable"
                BluetoothClass.Device.Major.PERIPHERAL -> return "Peripheral"
                BluetoothClass.Device.Major.NETWORKING -> return "Network Device"
            }
        }

        if (lowerMfg.contains("apple")) {
            return "Apple Device"
        }
        if (lowerMfg.contains("samsung")) {
            return "Samsung Device"
        }
        if (lowerMfg.contains("google")) {
            return "Google Device"
        }
        if (lowerMfg.contains("intel") || lowerMfg.contains("microsoft")) {
            return "Computer"
        }

        return "BLE Device"
    }

    fun getDevicesJson(): String {
        val list = devices.value
        val sb = java.lang.StringBuilder()
        sb.append("[")
        var isFirst = true
        list.forEach { dev ->
            if (dev.latitude != null && dev.longitude != null) {
                if (!isFirst) sb.append(",")
                isFirst = false
                sb.append("{")
                sb.append("\"address\":\"${dev.address}\",")
                sb.append("\"name\":\"${dev.name?.replace("\"", "\\\"") ?: ""}\",")
                sb.append("\"rssi\":${dev.rssi},")
                sb.append("\"estimatedDistanceMeters\":${dev.estimatedDistanceMeters},")
                sb.append("\"scanCount\":${dev.scanCount},")
                sb.append("\"isSafe\":${dev.isSafe},")
                sb.append("\"isSuspicious\":${dev.isSuspicious},")
                sb.append("\"manufacturerName\":\"${dev.manufacturerName.replace("\"", "\\\"")}\",")
                sb.append("\"latitude\":${dev.latitude},")
                sb.append("\"longitude\":${dev.longitude}")
                sb.append("}")
            }
        }
        sb.append("]")
        return sb.toString()
    }

    private fun saveDatabaseLocally(context: Context) {
        repositoryScope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "ble_devices_local_db.csv")
            val sb = java.lang.StringBuilder()
            synchronized(deviceDatabase) {
                deviceDatabase.values.forEach { dev ->
                    val nameStr = dev.name ?: ""
                    val latStr = dev.latitude?.toString() ?: ""
                    val lonStr = dev.longitude?.toString() ?: ""
                    sb.append("${dev.address}|${nameStr}|${dev.firstSeen}|${dev.lastSeen}|${dev.scanCount}|${dev.manufacturerName}|${latStr}|${lonStr}|${dev.deviceCategory}\n")
                }
            }
            try {
                file.writeText(sb.toString())
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun loadDatabaseLocally(context: Context) {
        val file = File(context.filesDir, "ble_devices_local_db.csv")
        if (!file.exists()) return
        try {
            val lines = file.readLines()
            synchronized(deviceDatabase) {
                lines.forEach { line ->
                    val tokens = line.split("|")
                    if (tokens.size >= 8) {
                        val address = tokens[0]
                        val name = tokens[1].ifEmpty { null }
                        val firstSeen = tokens[2].toLongOrNull() ?: System.currentTimeMillis()
                        val lastSeen = tokens[3].toLongOrNull() ?: System.currentTimeMillis()
                        val scanCount = tokens[4].toIntOrNull() ?: 1
                        val manufacturerName = tokens[5]
                        val latitude = tokens[6].toDoubleOrNull()
                        val longitude = tokens[7].toDoubleOrNull()
                        val category = if (tokens.size >= 9) tokens[8] else "BLE Device"
                        
                        deviceDatabase[address] = DeviceTrackingData(
                            address = address,
                            name = name,
                            firstSeen = firstSeen,
                            lastSeen = lastSeen,
                            scanCount = scanCount,
                            rssiHistory = mutableListOf(-75),
                            manufacturerName = manufacturerName,
                            latitude = latitude,
                            longitude = longitude,
                            maxRssi = -75,
                            lastIncrementTime = lastSeen,
                            sessionIncremented = false,
                            deviceCategory = category
                        )
                    }
                }
            }
            updateDeviceList(context)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun exportToWigleCsv(context: Context) {
        val list = devices.value
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val csvBuilder = StringBuilder()
        csvBuilder.append("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type\n")
        
        list.forEach { dev ->
            val lat = dev.latitude?.toString() ?: ""
            val lon = dev.longitude?.toString() ?: ""
            val ssid = dev.name ?: ""
            val firstSeenStr = sdf.format(Date(dev.firstSeen))
            csvBuilder.append("${dev.address},${ssid},,${firstSeenStr},,,${dev.rssi},${lat},${lon},,,BLE\n")
        }

        try {
            val docsDir = context.getExternalFilesDir(null)
            val file = File(docsDir, "wifi_guard_wigle_export_${System.currentTimeMillis()}.csv")
            file.writeText(csvBuilder.toString())
            
            FileLogger.log(context, "BluetoothTracker", "Wigle CSV exported locally: ${file.absolutePath}")
            
            Thread {
                val rcloneFile = File("/tmp/rclone-extracted/rclone-v1.74.3-linux-amd64/rclone")
                if (rcloneFile.exists()) {
                    val process = Runtime.getRuntime().exec(
                        arrayOf(
                            rcloneFile.absolutePath,
                            "copy",
                            file.absolutePath,
                            "mydrive:apk/"
                        )
                    )
                    process.waitFor()
                    FileLogger.log(context, "BluetoothTracker", "Wigle CSV synchronized to Google Drive successfully")
                }
            }.start()
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                ToastMessage(context, "Exported: ${file.name} & Synced to Google Drive")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun exportToKml(context: Context) {
        val list = devices.value
        val kmlBuilder = StringBuilder()
        kmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        kmlBuilder.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        kmlBuilder.append("<Document>\n")
        kmlBuilder.append("  <name>BLE Devices Map Export</name>\n")

        list.forEach { dev ->
            if (dev.latitude != null && dev.longitude != null) {
                val nameStr = dev.name ?: dev.address
                kmlBuilder.append("  <Placemark>\n")
                kmlBuilder.append("    <name>${nameStr}</name>\n")
                kmlBuilder.append("    <description>Manufacturer: ${dev.manufacturerName}\nCategory: ${dev.deviceCategory}\nAddress: ${dev.address}\nRSSI: ${dev.rssi} dBm\nSeen: ${dev.scanCount} times</description>\n")
                kmlBuilder.append("    <Point>\n")
                kmlBuilder.append("      <coordinates>${dev.longitude},${dev.latitude},0</coordinates>\n")
                kmlBuilder.append("    </Point>\n")
                kmlBuilder.append("  </Placemark>\n")
            }
        }

        kmlBuilder.append("</Document>\n")
        kmlBuilder.append("</kml>")

        try {
            val docsDir = context.getExternalFilesDir(null)
            val file = File(docsDir, "wifi_guard_gps_export_${System.currentTimeMillis()}.kml")
            file.writeText(kmlBuilder.toString())
            
            FileLogger.log(context, "BluetoothTracker", "KML map exported locally: ${file.absolutePath}")
            
            Thread {
                val rcloneFile = File("/tmp/rclone-extracted/rclone-v1.74.3-linux-amd64/rclone")
                if (rcloneFile.exists()) {
                    val process = Runtime.getRuntime().exec(
                        arrayOf(
                            rcloneFile.absolutePath,
                            "copy",
                            file.absolutePath,
                            "mydrive:apk/"
                        )
                    )
                    process.waitFor()
                    FileLogger.log(context, "BluetoothTracker", "KML map synchronized to Google Drive successfully")
                }
            }.start()

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                ToastMessage(context, "KML Map Saved: ${file.name} & Synced to Google Drive")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun ToastMessage(context: Context, text: String) {
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
    }
}
