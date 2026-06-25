package com.example.androidnetworkchecker.ui.main

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidnetworkchecker.data.BluetoothTrackerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val longitude: Double? = null,
    val deviceCategory: String = "BLE Device"
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

    private val _state = MutableStateFlow(BluetoothTrackerState())
    val state: StateFlow<BluetoothTrackerState> = _state.asStateFlow()

    private var observerJob: Job? = null

    init {
        BluetoothTrackerRepository.initialize(context)
        checkStatus()
        startObservingRepository()
    }

    fun checkStatus() {
        val permissionsGranted = hasRequiredPermissions()
        val btEnabled = bluetoothAdapter?.isEnabled == true
        _state.update {
            it.copy(
                hasPermissions = permissionsGranted,
                isBluetoothEnabled = btEnabled,
                errorMessage = when {
                    !permissionsGranted -> "Location/Bluetooth/Notification permissions required."
                    bluetoothAdapter == null -> "Bluetooth is not supported on this device."
                    !btEnabled -> "Bluetooth is turned off."
                    else -> null
                }
            )
        }
    }

    private fun startObservingRepository() {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            launch {
                BluetoothTrackerRepository.devices.collect { list ->
                    _state.update { it.copy(devices = list) }
                }
            }
            launch {
                BluetoothTrackerRepository.isScanning.collect { scanning ->
                    _state.update { it.copy(isScanning = scanning) }
                }
            }
        }
    }

    fun toggleSafeDevice(address: String) {
        BluetoothTrackerRepository.toggleSafeDevice(context, address)
    }

    fun clearDeviceHistory() {
        BluetoothTrackerRepository.clearDeviceHistory(context)
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPostNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            hasPostNotifications
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startScanning() {
        checkStatus()
        if (!_state.value.hasPermissions || !_state.value.isBluetoothEnabled) {
            return
        }

        try {
            val serviceIntent = Intent(context, BluetoothScanningService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            BluetoothTrackerRepository.setIsScanning(true)
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = "Failed to start background scanner service: ${e.localizedMessage}") }
        }
    }

    fun stopScanning() {
        try {
            val serviceIntent = Intent(context, BluetoothScanningService::class.java)
            context.stopService(serviceIntent)
            BluetoothTrackerRepository.setIsScanning(false)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun exportToWigleCsv(ctx: Context) {
        BluetoothTrackerRepository.exportToWigleCsv(ctx)
    }

    fun exportToKml(ctx: Context) {
        BluetoothTrackerRepository.exportToKml(ctx)
    }

    fun getDevicesJson(): String {
        return BluetoothTrackerRepository.getDevicesJson()
    }

    override fun onCleared() {
        super.onCleared()
        observerJob?.cancel()
    }
}
