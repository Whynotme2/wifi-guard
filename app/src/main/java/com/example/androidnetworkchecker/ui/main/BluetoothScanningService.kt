package com.example.androidnetworkchecker.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.androidnetworkchecker.MainActivity
import com.example.androidnetworkchecker.data.BluetoothTrackerRepository
import com.example.androidnetworkchecker.data.FileLogger

class BluetoothScanningService : Service() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var isScanning = false

    private val NOTIFICATION_ID = 8881
    private val CHANNEL_ID = "ble_background_scanner"

    override fun onCreate() {
        super.onCreate()
        BluetoothTrackerRepository.initialize(applicationContext)
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        
        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FileLogger.log(this, "BluetoothScanningService", "Service started onStartCommand")

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startScanning()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        FileLogger.log(this, "BluetoothScanningService", "Service destroyed")
        stopScanning()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "BLE Background Scan"
            val descriptionText = "Allows background monitoring of tracker beacons"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Anti-Stalker BLE Tracker Active")
            .setContentText("Scanning for BLE tracking devices in background...")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        isScanning = true
        BluetoothTrackerRepository.setIsScanning(true)
        startLocationUpdates()

        val filters = listOf(
            android.bluetooth.le.ScanFilter.Builder().build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
            FileLogger.log(this, "BluetoothScanningService", "BLE Background Scan started successfully")
        } catch (e: Exception) {
            isScanning = false
            BluetoothTrackerRepository.setIsScanning(false)
            FileLogger.log(this, "BluetoothScanningService", "Failed to start BLE background scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        if (!isScanning) return
        isScanning = false
        BluetoothTrackerRepository.setIsScanning(false)
        stopLocationUpdates()

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback)
                FileLogger.log(this, "BluetoothScanningService", "BLE Background Scan stopped successfully")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (locationManager == null) return
        try {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine) {
                if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                    locationManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        2000L,
                        2f,
                        locationListener
                    )
                }
                if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                    locationManager?.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000L,
                        2f,
                        locationListener
                    )
                }
                val gpsLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                lastLocation = gpsLoc ?: netLoc
            }
        } catch (e: Exception) {
            // Ignore
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
            BluetoothTrackerRepository.processScanResult(applicationContext, result, lastLocation)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach {
                BluetoothTrackerRepository.processScanResult(applicationContext, it, lastLocation)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            FileLogger.log(this@BluetoothScanningService, "BluetoothScanningService", "Scan failed: $errorCode")
            stopScanning()
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
}
