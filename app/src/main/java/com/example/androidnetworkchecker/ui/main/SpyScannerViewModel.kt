package com.example.androidnetworkchecker.ui.main

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import com.example.androidnetworkchecker.data.EmfScannerState
import com.example.androidnetworkchecker.data.SpyMode
import com.example.androidnetworkchecker.data.SpyScannerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt

class SpyScannerViewModel(context: Context) : ViewModel(), SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _emfState = MutableStateFlow(EmfScannerState())
    val emfState: StateFlow<EmfScannerState> = _emfState.asStateFlow()

    private val _spyState = MutableStateFlow(SpyScannerState())
    val spyState: StateFlow<SpyScannerState> = _spyState.asStateFlow()

    // Sensitivity parameter in uT (microteslas)
    val emfSensitivity = MutableStateFlow(20f)

    // Glint auto-detected coordinates (-1f means no glint detected)
    val glintX = MutableStateFlow(-1f)
    val glintY = MutableStateFlow(-1f)

    // Active color filter matrix index
    val activeColorFilterIndex = MutableStateFlow(0) // 0 = Negative Thermal, 1 = Night Vision, 2 = UV, 3 = High Contrast

    // AI Object Detection State
    val aiDetectedObject = MutableStateFlow("")
    val aiConfidence = MutableStateFlow(0f)

    fun updateAiObject(label: String, confidence: Float) {
        aiDetectedObject.value = label
        aiConfidence.value = confidence
    }

    // --- EMF Magnetometer Section ---
    fun startEmfScanning() {
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopEmfScanning() {
        sensorManager.unregisterListener(this)
    }

    fun calibrateBaseline() {
        _emfState.update { it.copy(baselineUt = it.currentUt, isCalibrated = true) }
    }

    fun setSensitivity(value: Float) {
        emfSensitivity.value = value
    }

    private var lastX = -1f
    private var lastY = -1f

    fun updateGlintCoordinates(x: Float, y: Float) {
        if (x < 0f || y < 0f) {
            glintX.value = -1f
            glintY.value = -1f
            lastX = -1f
            lastY = -1f
        } else {
            if (lastX < 0f || lastY < 0f) {
                lastX = x
                lastY = y
            } else {
                // Low-pass temporal filter (0.25f smoothing factor)
                lastX = lastX + 0.25f * (x - lastX)
                lastY = lastY + 0.25f * (y - lastY)
            }
            glintX.value = lastX
            glintY.value = lastY
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        _emfState.update { state ->
            val updatedHistory = (state.history + magnitude).takeLast(40)
            
            val currentBaseline = state.baselineUt
            val nextBaseline = if (!state.isAlertActive) {
                if (!state.isCalibrated) {
                    magnitude
                } else {
                    currentBaseline + 0.0005f * (magnitude - currentBaseline)
                }
            } else {
                currentBaseline
            }
            
            val deviance = Math.abs(magnitude - nextBaseline)
            state.copy(
                currentUt = magnitude,
                baselineUt = nextBaseline,
                isCalibrated = true,
                history = updatedHistory,
                isAlertActive = deviance > emfSensitivity.value
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- Glint Strobe Section ---
    fun toggleFlashStrobe(enabled: Boolean) {
        _spyState.update { it.copy(isStrobeActive = enabled) }
    }

    fun setGlintThreshold(value: Float) {
        _spyState.update { it.copy(glintThreshold = value) }
    }

    fun setSpyMode(mode: SpyMode) {
        _spyState.update { 
            it.copy(
                mode = mode,
                selectedCameraId = if (mode == SpyMode.GLINT_STROBE) "0" else "1"
            )
        }
        if (mode != SpyMode.GLINT_STROBE) {
            toggleFlashStrobe(false)
        }
    }

    fun toggleCamera(cameraId: String) {
        _spyState.update { it.copy(selectedCameraId = cameraId) }
    }

    fun toggleCamera() {
        _spyState.update { state ->
            val nextId = if (state.selectedCameraId == "0") "1" else "0"
            state.copy(selectedCameraId = nextId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopEmfScanning()
    }
}
