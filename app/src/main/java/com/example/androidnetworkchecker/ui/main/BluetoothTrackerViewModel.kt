package com.example.androidnetworkchecker.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

data class BluetoothTrackerDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val estimatedDistanceMeters: Double,
    val firstSeen: Long,
    val lastSeen: Long,
    val isSuspicious: Boolean,
    val isSafe: Boolean,
    val scanCount: Int,
    val manufacturerName: String,
    val isCurrentlyActive: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class BluetoothTrackerState(
    val isScanning: Boolean = false,
    val devices: List<BluetoothTrackerDevice> = emptyList(),
    val errorMessage: String? = null,
    val hasPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false
)

class BluetoothTrackerViewModel(private val context: Context) : ViewModel() {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var lastLocation: Location? = null

    private val _state = MutableStateFlow(BluetoothTrackerState())
    val state: StateFlow<BluetoothTrackerState> = _state.asStateFlow()

    // In-memory persistent tracking database
    private val deviceDatabase = mutableMapOf<String, DeviceTrackingData>()
    private val safeDevices = mutableSetOf<String>()

    private var cleanupJob: Job? = null
    private var isScanningInternal = false

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
        var maxRssi: Int = -999
    )

    // GPS location listener
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    init {
        checkStatus()
        startPeriodicCleanup()
    }

    fun checkStatus() {
        val permissionsGranted = hasRequiredPermissions()
        val btEnabled = bluetoothAdapter?.isEnabled == true
        _state.update {
            it.copy(
                hasPermissions = permissionsGranted,
                isBluetoothEnabled = btEnabled,
                errorMessage = when {
                    !permissionsGranted -> "Location/Bluetooth permissions required."
                    bluetoothAdapter == null -> "Bluetooth is not supported on this device."
                    !btEnabled -> "Bluetooth is turned off."
                    else -> null
                }
            )
        }
    }

    fun toggleSafeDevice(address: String) {
        if (safeDevices.contains(address)) {
            safeDevices.remove(address)
        } else {
            safeDevices.add(address)
        }
        updateDeviceList()
    }

    fun clearDeviceHistory() {
        synchronized(deviceDatabase) {
            deviceDatabase.clear()
        }
        updateDeviceList()
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        checkStatus()
        if (!_state.value.hasPermissions || !_state.value.isBluetoothEnabled) {
            return
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.update { it.copy(errorMessage = "Failed to obtain BLE Scanner.") }
            return
        }

        if (isScanningInternal) return
        isScanningInternal = true
        _state.update { it.copy(isScanning = true, errorMessage = null) }

        // Start listening for GPS coordinates updates
        startLocationUpdates()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            isScanningInternal = false
            _state.update { it.copy(isScanning = false, errorMessage = "Security Exception: ${e.localizedMessage}") }
        } catch (e: Exception) {
            isScanningInternal = false
            _state.update { it.copy(isScanning = false, errorMessage = "Scan error: ${e.localizedMessage}") }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanningInternal) return
        isScanningInternal = false
        _state.update { it.copy(isScanning = false) }

        // Stop GPS listener
        stopLocationUpdates()

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner != null && hasRequiredPermissions()) {
            try {
                scanner.stopScan(scanCallback)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (locationManager != null && hasRequiredPermissions()) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        2000L,
                        2f,
                        locationListener
                    )
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000L,
                        2f,
                        locationListener
                    )
                }
                val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                lastLocation = gpsLoc ?: netLoc
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update { it.copy(errorMessage = "BLE scan failed with error code: $errorCode") }
            stopScanning()
        }
    }

    private fun processScanResult(result: ScanResult) {
        val device = result.device
        val address = device.address
        val name = result.scanRecord?.deviceName ?: device.name
        val rssi = result.rssi
        val manufacturerName = getManufacturerName(result)
        val now = System.currentTimeMillis()

        synchronized(deviceDatabase) {
            val existing = deviceDatabase[address]
            val currentLoc = lastLocation
            if (existing != null) {
                existing.lastSeen = now
                existing.scanCount += 1
                existing.rssiHistory.add(rssi)
                if (existing.rssiHistory.size > 15) {
                    existing.rssiHistory.removeAt(0)
                }
                // Save coordinates at point of strongest signal strength
                if (rssi > existing.maxRssi && currentLoc != null) {
                    existing.latitude = currentLoc.latitude
                    existing.longitude = currentLoc.longitude
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
                    maxRssi = rssi
                )
            }
        }
        updateDeviceList()
    }

    private fun updateDeviceList() {
        val now = System.currentTimeMillis()
        val list = synchronized(deviceDatabase) {
            deviceDatabase.values.map { data ->
                val avgRssi = data.rssiHistory.average().toInt()
                val txPower = -60
                val estimatedDistance = calculateDistance(avgRssi, txPower)
                
                // Stalker detection rule:
                // Seen for >= 2 minutes (120000 ms) and average estimated distance is within 10 meters
                val timeSpan = now - data.firstSeen
                val isSuspicious = timeSpan >= 120000 && estimatedDistance <= 10.0 && !safeDevices.contains(data.address)

                // Currently active if seen within the last 30 seconds
                val isCurrentlyActive = (now - data.lastSeen) < 30000

                BluetoothTrackerDevice(
                    address = data.address,
                    name = data.name,
                    rssi = data.rssiHistory.lastOrNull() ?: avgRssi,
                    estimatedDistanceMeters = estimatedDistance,
                    firstSeen = data.firstSeen,
                    lastSeen = data.lastSeen,
                    isSuspicious = isSuspicious,
                    isSafe = safeDevices.contains(data.address),
                    scanCount = data.scanCount,
                    manufacturerName = data.manufacturerName,
                    isCurrentlyActive = isCurrentlyActive,
                    latitude = data.latitude,
                    longitude = data.longitude
                )
            }
        }

        // Sort checklist criteria: Times Found (scanCount) Descending, then Distance Ascending
        val sortedList = list.sortedWith(
            compareByDescending<BluetoothTrackerDevice> { it.scanCount }
                .thenBy { it.estimatedDistanceMeters }
        )

        _state.update { it.copy(devices = sortedList) }
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
                        "Apple Device (FindMy)"
                    }
                }
                0x0075 -> "Samsung SmartTag"
                0x00E0 -> "Google Tracker"
                0x0006 -> "Microsoft Beacon"
                0x0001 -> "Nokia BLE Device"
                0x0059 -> "Nordic Beacon"
                else -> "Generic BLE (ID: 0x${Integer.toHexString(id).uppercase()})"
            }
        }

        val serviceUuids = record.serviceUuids
        if (serviceUuids != null) {
            for (uuid in serviceUuids) {
                val uuidStr = uuid.toString().lowercase()
                if (uuidStr.contains("feed") || uuidStr.contains("fec9")) return "Tile Tracker"
                if (uuidStr.contains("fd5a")) return "Samsung SmartTag"
                if (uuidStr.contains("0000fd5a") || uuidStr.contains("0000feed")) return "BLE Tracker Tag"
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

    // JSON exporter string generator for Leaflet map integration
    fun getDevicesJson(): String {
        val list = state.value.devices
        val sb = StringBuilder()
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

    // Exports scan history to Wigle-compatible CSV and shares with file chooser (e.g., GDrive)
    fun exportToWigleCsv(context: Context) {
        val list = state.value.devices
        if (list.isEmpty()) {
            Toast.makeText(context, "No devices scanned to export.", Toast.LENGTH_SHORT).show()
            return
        }

        val csvBuilder = StringBuilder()
        // Wigle CSV Row 1: App details header
        csvBuilder.append("WigleWifi-1.4,appRelease=1.0,model=${Build.MODEL},device=${Build.DEVICE},display=${Build.DISPLAY},board=${Build.BOARD},brand=${Build.BRAND}\n")
        
        // Wigle CSV Row 2: Columns format
        csvBuilder.append("MAC,SSID,AuthMode,FirstSeen,Channel,Frequency,RSSI,Latitude,Longitude,Altitude,Accuracy,Type\n")
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        
        list.forEach { dev ->
            val firstSeenStr = sdf.format(Date(dev.firstSeen))
            val lat = dev.latitude?.toString() ?: ""
            val lon = dev.longitude?.toString() ?: ""
            val ssid = dev.name ?: ""
            csvBuilder.append("${dev.address},${ssid},,${firstSeenStr},,,${dev.rssi},${lat},${lon},,,BLE\n")
        }

        val csvData = csvBuilder.toString()
        val filename = "wifiguard_ble_scan_${System.currentTimeMillis()}.csv"
        val file = File(context.getExternalFilesDir(null), filename)

        try {
            file.writeText(csvData)
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "WifiGuard BLE Tracker Scan logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Upload Wigle CSV to Google Drive"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to write export log: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startPeriodicCleanup() {
        cleanupJob?.cancel()
        cleanupJob = viewModelScope.launch {
            while (true) {
                delay(10000)
                updateDeviceList()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
        cleanupJob?.cancel()
    }
}
