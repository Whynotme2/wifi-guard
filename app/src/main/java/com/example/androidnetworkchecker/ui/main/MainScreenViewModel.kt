package com.example.androidnetworkchecker.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidnetworkchecker.data.DataRepository
import com.example.androidnetworkchecker.data.NetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class MainScreenViewModel(private val dataRepository: DataRepository) : ViewModel() {
    
    val uiState: StateFlow<NetworkState> = dataRepository.networkState

    private var scanJob: Job? = null
    private var portScanJob: Job? = null
    private var tracerouteJob: Job? = null
    private var pingJitterJob: Job? = null
    private var dnsBenchmarkJob: Job? = null
    private var geoLocateJob: Job? = null
    private var wifiSignalJob: Job? = null

    fun refreshNetworkState(context: Context) {
        dataRepository.refreshNetworkState(context)
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.fetchPublicIpOverview()
        }
    }

    fun startWifiSignalMonitoring(context: Context) {
        wifiSignalJob?.cancel()
        wifiSignalJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                dataRepository.refreshWifiSignalState(context)
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    fun stopWifiSignalMonitoring() {
        wifiSignalJob?.cancel()
    }

    fun runDnsLeakTest() {
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.performDnsLeakTest { _ -> }
        }
    }

    fun clearDnsLeakTest() {
        dataRepository.clearDnsLeakTestResult()
    }

    fun startSubnetScan(context: Context, interfaceName: String, localIp: String, prefixLength: Int) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            dataRepository.startSubnetScan(context, interfaceName, localIp, prefixLength)
        }
    }

    fun cancelSubnetScan() {
        scanJob?.cancel()
        dataRepository.cancelSubnetScan()
    }

    fun resetSubnetScan() {
        scanJob?.cancel()
        dataRepository.clearScanState()
    }

    fun startPortScan(ip: String) {
        portScanJob?.cancel()
        portScanJob = viewModelScope.launch(Dispatchers.IO) {
            dataRepository.startPortScan(ip)
        }
    }

    fun startPortScanCustom(ip: String, ports: List<Int>) {
        portScanJob?.cancel()
        portScanJob = viewModelScope.launch(Dispatchers.IO) {
            dataRepository.startPortScanCustom(ip, ports)
        }
    }

    fun cancelPortScan() {
        portScanJob?.cancel()
        dataRepository.cancelPortScan()
    }

    fun resetPortScan() {
        portScanJob?.cancel()
        dataRepository.clearPortScanState()
    }

    fun startTraceroute(target: String) {
        tracerouteJob?.cancel()
        tracerouteJob = viewModelScope.launch(Dispatchers.IO) {
            dataRepository.startTraceroute(target)
        }
    }

    fun cancelTraceroute() {
        tracerouteJob?.cancel()
        dataRepository.cancelTraceroute()
    }

    fun resetTraceroute() {
        tracerouteJob?.cancel()
        dataRepository.clearTracerouteState()
    }

    fun startPingJitter(target: String) {
        pingJitterJob?.cancel()
        pingJitterJob = viewModelScope.launch(Dispatchers.IO) {
            dataRepository.startPingJitter(target)
        }
    }

    fun cancelPingJitter() {
        pingJitterJob?.cancel()
        dataRepository.cancelPingJitter()
    }

    fun resetPingJitter() {
        pingJitterJob?.cancel()
        dataRepository.clearPingJitterState()
    }

    fun toggleVpnWatchdog(context: Context, enabled: Boolean) {
        dataRepository.toggleVpnWatchdog(context, enabled)
    }

    fun toggleWifiIntruder(context: Context, enabled: Boolean) {
        dataRepository.toggleWifiIntruder(context, enabled)
    }

    fun simulateIntruderAlert() {
        dataRepository.simulateIntruderAlert()
    }

    fun sendWakeOnLan(macAddress: String) {
        dataRepository.sendWakeOnLan(macAddress)
    }

    fun startDnsBenchmark(domain: String) {
        dnsBenchmarkJob?.cancel()
        dnsBenchmarkJob = viewModelScope.launch {
            dataRepository.runDnsBenchmark(domain)
        }
    }

    fun startGeoLocation(target: String) {
        geoLocateJob?.cancel()
        geoLocateJob = viewModelScope.launch {
            dataRepository.runGeoLocation(target)
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        portScanJob?.cancel()
        tracerouteJob?.cancel()
        pingJitterJob?.cancel()
        dnsBenchmarkJob?.cancel()
        geoLocateJob?.cancel()
        wifiSignalJob?.cancel()
    }
}
