package com.example.androidnetworkchecker.ui.main

import java.io.File
import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.ImageReader
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.androidnetworkchecker.data.FileLogger
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.androidnetworkchecker.data.EmfScannerState
import com.example.androidnetworkchecker.data.SpyMode
import com.example.androidnetworkchecker.data.SpyScannerState
import com.example.androidnetworkchecker.data.TFLiteObjectDetector
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// Theme Colors
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

@Composable
fun SpyDetectorTab(viewModel: SpyScannerViewModel, bluetoothViewModel: BluetoothTrackerViewModel) {
    val emfState by viewModel.emfState.collectAsState()
    val spyState by viewModel.spyState.collectAsState()
    var activeSubTab by rememberSaveable { mutableStateOf(0) } // 0 = Wall EMF, 1 = Optics & IR, 2 = AI Object Scan, 3 = BT Tracker

    DisposableEffect(Unit) {
        viewModel.startEmfScanning()
        onDispose {
            viewModel.stopEmfScanning()
            viewModel.toggleFlashStrobe(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        // Sub Tab Row
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Slate900,
            contentColor = Teal500
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("Wall EMF Scanner", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (activeSubTab == 0) Teal500 else Slate400) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("Optics & IR Finder", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (activeSubTab == 1) Teal500 else Slate400) }
            )
            Tab(
                selected = activeSubTab == 2,
                onClick = { activeSubTab = 2 },
                text = { Text("AI Object Scanner", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (activeSubTab == 2) Teal500 else Slate400) }
            )
            Tab(
                selected = activeSubTab == 3,
                onClick = { activeSubTab = 3 },
                text = { Text("BT Tracker", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (activeSubTab == 3) Teal500 else Slate400) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
        ) {
            when (activeSubTab) {
                0 -> Box(modifier = Modifier.padding(horizontal = 16.dp)) { WallEmfScannerView(emfState = emfState, viewModel = viewModel) }
                1 -> Box(modifier = Modifier.padding(horizontal = 16.dp)) { OpticsCamView(spyState = spyState, viewModel = viewModel) }
                2 -> Box(modifier = Modifier.padding(horizontal = 16.dp)) { AiObjectDetectionView(viewModel = viewModel) }
                3 -> BluetoothTrackerTab(viewModel = bluetoothViewModel)
            }
        }
    }
}

@Composable
fun WallEmfScannerView(emfState: EmfScannerState, viewModel: SpyScannerViewModel) {
    val sensitivity by viewModel.emfSensitivity.collectAsState()
    var showArRadar by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val sensorManager = remember { context.getSystemService(SensorManager::class.java) as SensorManager }
    val hasMagnetometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null }

    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (isGranted) {
                showArRadar = true
            } else {
                Toast.makeText(context, "Camera permission is required for AR Wall Wire Radar.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Concealed AC Wire & Bug Locator",
                color = Slate50,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (!hasMagnetometer) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .border(1.dp, Amber500.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Amber500.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Amber500,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = "Magnetometer Required",
                            color = Slate50,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Your device lacks a hardware magnetic field sensor (magnetometer) which is necessary to pick up electromagnetic waves from hidden wires. This utility is disabled.",
                            color = Slate400,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (showArRadar && hasCameraPermission) {
                item {
                Text(
                    text = "AR Wall Scanner Mode: Sweep against wall flatly",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Slate700, RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    // Negative thermal filter color matrix
                    val negativeThermalMatrix = floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -0.7f, 0f, 0f, 180f,
                        0f, 0f, -0.7f, 0f, 180f,
                        0f, 0f, 0f, 1f, 0f
                    )

                    CameraPreview3(
                        cameraId = "0", // Rear Camera
                        colorMatrix = negativeThermalMatrix,
                        isStrobeActive = false,
                        onGlintDetected = { _, _ -> },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Draw scanning wire grids
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridCount = 8
                        val stepX = size.width / gridCount
                        val stepY = size.height / gridCount

                        // Draw Grid lines
                        for (i in 1 until gridCount) {
                            drawLine(
                                color = Color.Cyan.copy(alpha = 0.25f),
                                start = androidx.compose.ui.geometry.Offset(i * stepX, 0f),
                                end = androidx.compose.ui.geometry.Offset(i * stepX, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = Color.Cyan.copy(alpha = 0.25f),
                                start = androidx.compose.ui.geometry.Offset(0f, i * stepY),
                                end = androidx.compose.ui.geometry.Offset(size.width, i * stepY),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Draw glowing neon wire paths if EMF spike detected
                        if (emfState.isAlertActive) {
                            val pulseScale = 1f + 0.1f * sin(System.currentTimeMillis() / 200.0).toFloat()
                            
                            // Horizontal wire path representation in neon red/cyan
                            drawRect(
                                color = Red500.copy(alpha = 0.4f),
                                topLeft = Offset(0f, size.height / 2f - 20.dp.toPx() * pulseScale),
                                size = Size(size.width, 40.dp.toPx() * pulseScale)
                            )
                            drawLine(
                                color = Red500,
                                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                                strokeWidth = 4.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )

                            // Glowing scan text
                            drawCircle(
                                color = Red500.copy(alpha = 0.2f),
                                radius = 60.dp.toPx() * pulseScale,
                                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            )
                        }
                    }

                    // Value overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "EMF: ${emfState.currentUt.toInt()} µT",
                            color = if (emfState.isAlertActive) Red500 else Teal500,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Close radar button
                    IconButton(
                        onClick = { showArRadar = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(32.dp)
                    ) {
                        Text("X", color = Slate50, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            item {
                // Circular EMF Gauge Canvas
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(190.dp)
                ) {
                    val animatedUt by animateFloatAsState(
                        targetValue = emfState.currentUt.coerceIn(0f, 200f),
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    )
                    Canvas(modifier = Modifier.size(170.dp)) {
                        val startAngle = 135f
                        val sweepAngle = 270f
                        
                        // Track arc
                        drawArc(
                            color = Slate700,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 12.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )

                        // Fill color based on danger level
                        val fillColor = when {
                            animatedUt > (emfState.baselineUt + sensitivity) -> Red500
                            animatedUt > (emfState.baselineUt + sensitivity / 2) -> Amber500
                            else -> Emerald500
                        }

                        // Active signal arc
                        val fillSweep = (animatedUt / 200f) * sweepAngle
                        drawArc(
                            color = fillColor,
                            startAngle = startAngle,
                            sweepAngle = fillSweep,
                            useCenter = false,
                            style = Stroke(width = 12.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )

                        // Needle calculations
                        val angleRad = Math.toRadians((startAngle + fillSweep).toDouble())
                        val needleLength = size.width / 2.2f
                        val center = size.width / 2f
                        val endX = center + needleLength * cos(angleRad).toFloat()
                        val endY = center + needleLength * sin(angleRad).toFloat()

                        // Draw needle line
                        drawLine(
                            color = Slate50,
                            start = androidx.compose.ui.geometry.Offset(center, center),
                            end = androidx.compose.ui.geometry.Offset(endX, endY),
                            strokeWidth = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )

                        // Needle hub
                        drawCircle(
                            color = Slate50,
                            radius = 7.dp.toPx()
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = 50.dp)
                    ) {
                        Text(
                            text = "${emfState.currentUt.toInt()} µT",
                            color = Slate50,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (emfState.isAlertActive) "EMF SPIKE DETECTED" else "NORMAL BACKGROUND",
                            color = if (emfState.isAlertActive) Red500 else Slate400,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.calibrateBaseline() },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Calibrate Baseline", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (!hasCameraPermission) {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                showArRadar = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Activate AR Wire Radar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            // Sensitivity Slider Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate700, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EMF Sensitivity Tuning", color = Slate50, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("${sensitivity.toInt()} µT Deviation", color = Teal500, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Slider(
                        value = sensitivity,
                        onValueChange = { viewModel.setSensitivity(it) },
                        valueRange = 5f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = Teal500,
                            activeTrackColor = Teal500,
                            inactiveTrackColor = Slate700
                        )
                    )
                    Text(
                        "Lower values increase detection range for deep/thin wiring; raise values to ignore electrical household noise.",
                        color = Slate400,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        item {
            // Guide Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate700, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Teal500)
                    Column {
                        Text("How to scan walls:", color = Slate50, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "1. Place the back of the phone flat against the wall.\n" +
                            "2. Calibrate baseline to clear natural house averages.\n" +
                            "3. Sweep the phone slowly. A deviation spike exceeding your custom sensitivity limit reveals live structural wire paths or bugs.",
                            color = Slate400,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
fun OpticsCamView(spyState: SpyScannerState, viewModel: SpyScannerViewModel) {
    val context = LocalContext.current
    val filterIndex by viewModel.activeColorFilterIndex.collectAsState()
    val glintX by viewModel.glintX.collectAsState()
    val glintY by viewModel.glintY.collectAsState()

    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                Toast.makeText(context, "Camera permission is required to search for lens reflections.", Toast.LENGTH_LONG).show()
            }
        }
    )

    var isFullScreen by rememberSaveable { mutableStateOf(true) }
    var zoomLevel by rememberSaveable { mutableStateOf(1.0f) }
    var isRecording by rememberSaveable { mutableStateOf(false) }

    // Instantiate local TFLite detector in OpticsCamView for hybrid AI/CV lens finder
    val detector = remember { TFLiteObjectDetector(context) }
    var aiDetectedObject by remember { mutableStateOf("") }
    var aiConfidence by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose {
            detector.close()
        }
    }

    // Color matrix definitions
    val negativeThermalMatrix = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,   // Inverted Red
        0f, -0.7f, 0f, 0f, 180f, // Inverted Green
        0f, 0f, -0.7f, 0f, 180f, // Inverted Blue
        0f, 0f, 0f, 1f, 0f
    )

    val nightVisionMatrix = floatArrayOf(
        0f, 0f, 0f, 0f, 0f,
        0.3f, 1.5f, 0.3f, 0f, -30f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    val uvMatrix = floatArrayOf(
        0.5f, 0f, 0.8f, 0f, 30f,
        0f, 0f, 0f, 0f, 0f,
        0.8f, 0f, 1.5f, 0f, 50f,
        0f, 0f, 0f, 1f, 0f
    )

    val glintHighContrastMatrix = floatArrayOf(
        5f, 5f, 5f, 0f, -800f,
        5f, 5f, 5f, 0f, -800f,
        5f, 5f, 5f, 0f, -800f,
        0f, 0f, 0f, 1f, 0f
    )

    val filters = listOf(
        "Thermal (Red/Cyan)" to negativeThermalMatrix,
        "Night Vision" to nightVisionMatrix,
        "UV Purple" to uvMatrix,
        "High Contrast (Strobe)" to glintHighContrastMatrix
    )

    val currentMatrix = filters[filterIndex].second
    val currentCameraId = spyState.selectedCameraId

    // Define reusable Camera Preview Composable Content
    val cameraPreviewContent = @Composable { modifier: Modifier ->
        Box(modifier = modifier.background(Color.Black)) {
            CameraPreview3(
                cameraId = currentCameraId,
                colorMatrix = currentMatrix,
                isStrobeActive = spyState.isStrobeActive,
                zoomLevel = zoomLevel,
                isRecording = isRecording,
                onGlintDetected = { x, y ->
                    viewModel.updateGlintCoordinates(x, y)
                },
                detector = detector,
                onObjectDetected = { label, confidence ->
                    aiDetectedObject = label
                    aiConfidence = confidence
                },
                modifier = Modifier.fillMaxSize()
            )

            // Switch Camera Button
            IconButton(
                onClick = { viewModel.toggleCamera() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Switch Camera",
                    tint = Slate50,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Full Screen Toggle Button
            IconButton(
                onClick = { isFullScreen = !isFullScreen },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 40.dp, start = 8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Toggle Fullscreen",
                    tint = Slate50,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Record Button (Red Dot / White Square)
            IconButton(
                onClick = { isRecording = !isRecording },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(if (isRecording) Red500 else Color.Black.copy(alpha = 0.6f), CircleShape)
                    .size(52.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(if (isRecording) RoundedCornerShape(4.dp) else CircleShape)
                        .background(if (isRecording) Slate50 else Red500)
                )
            }

            // Zoom Slider Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 80.dp)
                    .width(150.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Zoom", color = Slate50, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(String.format(java.util.Locale.US, "%.1fx", zoomLevel), color = Teal500, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = zoomLevel,
                    onValueChange = { zoomLevel = it },
                    valueRange = 1.0f..4.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Teal500,
                        activeTrackColor = Teal500,
                        inactiveTrackColor = Slate700
                    )
                )
            }

            // Render glowing Target Crosshairs if Glint is Auto-Detected
            if (glintX >= 0f && glintY >= 0f) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val radiusPulse by infiniteTransition.animateFloat(
                    initialValue = 12.dp.value,
                    targetValue = 24.dp.value,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "radius"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pixelX = glintX * size.width
                    val pixelY = glintY * size.height

                    // Draw target pulsing circle
                    drawCircle(
                        color = Red500.copy(alpha = 0.4f),
                        radius = radiusPulse.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(pixelX, pixelY)
                    )
                    drawCircle(
                        color = Red500,
                        radius = 6.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(pixelX, pixelY)
                    )

                    // Draw target crosshairs lines
                    drawLine(
                        color = Red500,
                        start = androidx.compose.ui.geometry.Offset(pixelX - 30.dp.toPx(), pixelY),
                        end = androidx.compose.ui.geometry.Offset(pixelX + 30.dp.toPx(), pixelY),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = Red500,
                        start = androidx.compose.ui.geometry.Offset(pixelX, pixelY - 30.dp.toPx()),
                        end = androidx.compose.ui.geometry.Offset(pixelX, pixelY + 30.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                // Alarms warning overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 12.dp, vertical = 80.dp)
                        .background(Red500, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LENS REFLECTION SPOTTED",
                        color = Slate50,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            }

            // AI camera warning banner
            if (aiDetectedObject.isNotEmpty()) {
                val cleanLabel = aiDetectedObject.split(",").firstOrNull()?.trim() ?: aiDetectedObject
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp)
                        .background(Red500.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "AI DETECTED: ${cleanLabel.uppercase()} (${(aiConfidence * 100).toInt()}%)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // Instruction banner overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(
                    text = if (spyState.mode == SpyMode.GLINT_STROBE) 
                        "STROBE OPTICS FINDER: Scan and look for the pulsing target locator." 
                        else "IR SCANNER: Scan dark spaces to reveal hidden IR night-vision LEDs.",
                    color = if (spyState.mode == SpyMode.GLINT_STROBE) Teal500 else Indigo500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (spyState.mode == SpyMode.GLINT_STROBE) {
                // Strobe toggle switcher
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Strobe Flash", color = Slate50, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = spyState.isStrobeActive,
                        onCheckedChange = { viewModel.toggleFlashStrobe(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Teal500,
                            checkedTrackColor = Teal500.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    }

    if (isFullScreen && hasCameraPermission && spyState.mode != SpyMode.OFF) {
        Box(modifier = Modifier.fillMaxSize()) {
            cameraPreviewContent(Modifier.fillMaxSize())
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Toggle modes button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            viewModel.setSpyMode(SpyMode.GLINT_STROBE)
                            viewModel.activeColorFilterIndex.value = 3 // default to High Contrast for glint strobe
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (spyState.mode == SpyMode.GLINT_STROBE) Teal500 else Slate800
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Lens Finder (Strobe)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate50)
                }

                Button(
                    onClick = {
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            viewModel.setSpyMode(SpyMode.IR_THERMAL)
                            viewModel.activeColorFilterIndex.value = 0 // default to Thermal for IR scanner
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (spyState.mode == SpyMode.IR_THERMAL) Indigo500 else Slate800
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("IR Scanner (Front)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate50)
                }
            }

            if (!hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate800)
                        .border(1.dp, Slate700, RoundedCornerShape(12.dp))
                        .clickable { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Slate400, modifier = Modifier.size(32.dp))
                        Text("Grant Camera Permission to Scan", color = Slate400, fontSize = 13.sp)
                    }
                }
            } else if (spyState.mode == SpyMode.OFF) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate800)
                        .border(1.dp, Slate700, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Select scanning mode above", color = Slate400, fontSize = 13.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    cameraPreviewContent(Modifier.fillMaxSize())
                }

                // Colors selector bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Visual Color Palettes",
                        color = Slate400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filters.forEachIndexed { idx, filter ->
                            Button(
                                onClick = { viewModel.activeColorFilterIndex.value = idx },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (filterIndex == idx) Teal500 else Slate800
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = filter.first.substringBefore(" ("),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate50,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CameraPreview3(
    cameraId: String,
    colorMatrix: FloatArray?,
    isStrobeActive: Boolean,
    zoomLevel: Float = 1.0f,
    isRecording: Boolean = false,
    onGlintDetected: (Float, Float) -> Unit,
    detector: TFLiteObjectDetector? = null,
    onObjectDetected: ((String, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnGlintDetected by rememberUpdatedState(onGlintDetected)
    data class Peak(val x: Int, val y: Int, val value: Int)

    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var previewRequestBuilder by remember { mutableStateOf<CaptureRequest.Builder?>(null) }

    // Control camera device lifecycle via LaunchedEffect
    LaunchedEffect(cameraId, textureViewRef, isRecording) {
        val textureView = textureViewRef ?: return@LaunchedEffect
        var isCancelled = false
        var imageReader: ImageReader? = null
        var isAiLensDetected = false
        val handlerThread = android.os.HandlerThread("CameraBackground").apply { start() }
        val backgroundHandler = android.os.Handler(handlerThread.looper)
        
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var previewWidth = 1280
        var previewHeight = 720
        var readerWidth = 640
        var readerHeight = 360

        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map != null) {
                val targetRatio = 16f / 9f
                val textureSizes = map.getOutputSizes(SurfaceTexture::class.java)
                if (textureSizes != null && textureSizes.isNotEmpty()) {
                    val optimalPreview = chooseOptimalSize(textureSizes, targetRatio, 1920 * 1080)
                    previewWidth = optimalPreview.width
                    previewHeight = optimalPreview.height
                }
                val yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888)
                if (yuvSizes != null && yuvSizes.isNotEmpty()) {
                    val optimalReader = chooseOptimalSize(yuvSizes, targetRatio, 640 * 360)
                    readerWidth = optimalReader.width
                    readerHeight = optimalReader.height
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var mediaRecorder: android.media.MediaRecorder? = null
        var videoFile: File? = null

        if (isRecording) {
            try {
                val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    android.media.MediaRecorder(context)
                } else {
                    android.media.MediaRecorder()
                }

                recorder.setVideoSource(android.media.MediaRecorder.VideoSource.SURFACE)
                recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                recorder.setVideoEncoder(android.media.MediaRecorder.VideoEncoder.H264)
                recorder.setVideoEncodingBitRate(10000000)
                recorder.setVideoFrameRate(30)
                recorder.setVideoSize(previewWidth, previewHeight)

                val docsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                val file = File(docsDir, "wifi_guard_rec_${System.currentTimeMillis()}.mp4")
                videoFile = file
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare()
                mediaRecorder = recorder

                FileLogger.log(context, "CameraPreview", "MediaRecorder prepared at path: ${file.absolutePath}")
            } catch (e: Exception) {
                FileLogger.log(context, "CameraPreview", "Failed to prepare MediaRecorder: ${e.message}")
                e.printStackTrace()
            }
        }
        
        FileLogger.log(context, "CameraPreview", "LaunchedEffect triggered for cameraId=$cameraId (isRecording=$isRecording)")
              val openCamera = { surfaceTexture: SurfaceTexture, _: Int, _: Int ->
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            try {
                FileLogger.log(context, "CameraPreview", "Opening camera $cameraId (surface: ${previewWidth}x${previewHeight})")
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (isCancelled) {
                            FileLogger.log(context, "CameraPreview", "Camera $cameraId opened but coroutine was already cancelled. Closing immediately.")
                            camera.close()
                            return
                        }
                        
                        FileLogger.log(context, "CameraPreview", "Camera $cameraId opened successfully")
                        cameraDevice = camera
                        val surface = Surface(surfaceTexture)
                        surfaceTexture.setDefaultBufferSize(previewWidth, previewHeight)

                        // Set up ImageReader for background glint analysis
                        var lastInferenceTime = 0L
                        val history = java.util.ArrayDeque<List<Peak>>()
                        val reader = ImageReader.newInstance(readerWidth, readerHeight, ImageFormat.YUV_420_888, 2)
                        imageReader = reader
                        reader.setOnImageAvailableListener({ r ->
                            try {
                                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                                try {
                                    val nowTime = System.currentTimeMillis()
                                    if (detector != null && onObjectDetected != null && nowTime - lastInferenceTime >= 350) {
                                        lastInferenceTime = nowTime
                                        val rotDeg = getRotationDegrees(context, cameraId)
                                        val result = detector.classifyFrame(image, rotDeg)
                                        if (result != null) {
                                            val lowerLabel = result.first.lowercase()
                                            isAiLensDetected = lowerLabel.contains("lens") || lowerLabel.contains("camera") || lowerLabel.contains("glass") || lowerLabel.contains("optic")
                                            onObjectDetected(result.first, result.second)
                                        } else {
                                            isAiLensDetected = false
                                            onObjectDetected("", 0f)
                                        }
                                    }

                                    val planes = image.planes
                                    val yPlane = planes[0]
                                    val buffer = yPlane.buffer
                                    val w = image.width
                                    val h = image.height
                                    val rowStride = yPlane.rowStride
                                    val pixelStride = yPlane.pixelStride

                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)

                                    val validCandidates = mutableListOf<Peak>()

                                    // Step by 2 to cover the image space efficiently
                                    for (y in 2 until h - 2 step 2) {
                                        for (x in 2 until w - 2 step 2) {
                                            val index = y * rowStride + x * pixelStride
                                            if (index < bytes.size) {
                                                val value = bytes[index].toInt() and 0xFF
                                                if (value >= 250) {
                                                    // 1. Check if it is a local peak compared to its 3x3 neighbors
                                                    var isPeak = true
                                                    for (ny in (y - 1)..(y + 1)) {
                                                        for (nx in (x - 1)..(x + 1)) {
                                                            if (ny != y || nx != x) {
                                                                val nIdx = ny * rowStride + nx * pixelStride
                                                                if (nIdx < bytes.size && (bytes[nIdx].toInt() and 0xFF) > value) {
                                                                    isPeak = false
                                                                    break
                                                                }
                                                            }
                                                        }
                                                        if (!isPeak) break
                                                    }

                                                    if (isPeak) {
                                                        // 2. Spatial validation checks for local peak
                                                        // Distance 1 neighbors average
                                                        var sum1 = 0
                                                        var count1 = 0
                                                        for (ny in (y - 1)..(y + 1)) {
                                                            for (nx in (x - 1)..(x + 1)) {
                                                                if (ny != y || nx != x) {
                                                                    val nIdx = ny * rowStride + nx * pixelStride
                                                                    if (nIdx < bytes.size) {
                                                                        sum1 += bytes[nIdx].toInt() and 0xFF
                                                                        count1++
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        val avg1 = if (count1 > 0) sum1 / count1 else value

                                                        // Distance 2 neighbors average
                                                        var sum2 = 0
                                                        var count2 = 0
                                                        for (ny in (y - 2)..(y + 2)) {
                                                            for (nx in (x - 2)..(x + 2)) {
                                                                if (Math.abs(ny - y) == 2 || Math.abs(nx - x) == 2) {
                                                                    val nIdx = ny * rowStride + nx * pixelStride
                                                                    if (nIdx < bytes.size) {
                                                                        sum2 += bytes[nIdx].toInt() and 0xFF
                                                                        count2++
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        val avg2 = if (count2 > 0) sum2 / count2 else value

                                                        if (value - avg1 >= 30 && value - avg2 >= 70) {
                                                            validCandidates.add(Peak(x, y, value))
                                                            if (validCandidates.size >= 5) break
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (validCandidates.size >= 5) break
                                    }

                                    // 3. Temporal pulsing check
                                    var bestGlint: Peak? = null
                                    for (cand in validCandidates) {
                                        var matchCount = 0
                                        for (pastFramePeaks in history) {
                                            var foundMatch = false
                                            for (pastPeak in pastFramePeaks) {
                                                val dx = pastPeak.x - cand.x
                                                val dy = pastPeak.y - cand.y
                                                if (dx * dx + dy * dy <= 15 * 15) {
                                                    foundMatch = true
                                                    break
                                                }
                                            }
                                            if (foundMatch) {
                                                matchCount++
                                            }
                                        }

                                        if (matchCount in 3..8) {
                                            if (bestGlint == null || cand.value > bestGlint.value) {
                                                bestGlint = cand
                                            }
                                        }
                                    }

                                    // Add current frame candidates to history
                                    history.addLast(validCandidates)
                                    if (history.size > 12) {
                                        history.removeFirst()
                                    }

                                    if ((isStrobeActive || isAiLensDetected) && bestGlint != null) {
                                        currentOnGlintDetected(bestGlint.x.toFloat() / w, bestGlint.y.toFloat() / h)
                                    } else {
                                        currentOnGlintDetected(-1f, -1f)
                                    }
                                } catch (e: Exception) {
                                    FileLogger.log(context, "CameraPreview", "Error analyzing frame: ${e.message}")
                                    e.printStackTrace()
                                } finally {
                                    image.close()
                                }
                            } catch (e: Exception) {
                                FileLogger.log(context, "CameraPreview", "Error acquiring image: ${e.message}")
                                e.printStackTrace()
                            }
                        }, backgroundHandler)

                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            addTarget(reader.surface)
                            mediaRecorder?.surface?.let { addTarget(it) }

                            // Apply zoom region on initialization
                            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                            val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                            if (activeArraySize != null) {
                                val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                                val zoom = zoomLevel.coerceIn(1.0f, maxZoom)
                                val cropW = (activeArraySize.width() / zoom).toInt()
                                val cropH = (activeArraySize.height() / zoom).toInt()
                                val deltaW = activeArraySize.width() - cropW
                                val deltaH = activeArraySize.height() - cropH
                                val cropRect = android.graphics.Rect(
                                    deltaW / 2,
                                    deltaH / 2,
                                    activeArraySize.width() - deltaW / 2,
                                    activeArraySize.height() - deltaH / 2
                                )
                                set(CaptureRequest.SCALER_CROP_REGION, cropRect)
                            }
                        }
                        previewRequestBuilder = builder

                        val sessionTargets = mutableListOf<Surface>(surface, reader.surface)
                        mediaRecorder?.surface?.let { sessionTargets.add(it) }

                        camera.createCaptureSession(
                            sessionTargets,
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    if (isCancelled) {
                                        FileLogger.log(context, "CameraPreview", "Session configured but coroutine was already cancelled. Closing session immediately.")
                                        session.close()
                                        return
                                    }
                                    
                                    FileLogger.log(context, "CameraPreview", "Capture session configured for cameraId=$cameraId")
                                    captureSession = session
                                    try {
                                        session.setRepeatingRequest(builder.build(), null, null)
                                        mediaRecorder?.start()
                                        FileLogger.log(context, "CameraPreview", "Recording started successfully")
                                    } catch (e: Exception) {
                                        FileLogger.log(context, "CameraPreview", "Error starting repeating request/recording: ${e.message}")
                                        e.printStackTrace()
                                    }
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    FileLogger.log(context, "CameraPreview", "Capture session configuration failed for cameraId=$cameraId")
                                }
                            },
                            null
                        )
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        FileLogger.log(context, "CameraPreview", "Camera $cameraId disconnected")
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        FileLogger.log(context, "CameraPreview", "Camera $cameraId reported error: $error")
                        camera.close()
                        cameraDevice = null
                    }
                }, null)
            } catch (e: Exception) {
                FileLogger.log(context, "CameraPreview", "Exception inside openCamera: ${e.message}")
                e.printStackTrace()
            }
        }
        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                openCamera(st, w, h)
                textureViewRef?.let { tv ->
                    configureTransform(tv, w, h, previewWidth, previewHeight, context, cameraId)
                }
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                textureViewRef?.let { tv ->
                    configureTransform(tv, w, h, previewWidth, previewHeight, context, cameraId)
                }
            }
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        textureView.surfaceTextureListener = listener

        if (textureView.isAvailable) {
            val st = textureView.surfaceTexture
            if (st != null) {
                openCamera(st, textureView.width, textureView.height)
                configureTransform(textureView, textureView.width, textureView.height, previewWidth, previewHeight, context, cameraId)
            }
        }
        // Cleanup coroutine block closes camera device gracefully on disposal
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            isCancelled = true
            FileLogger.log(context, "CameraPreview", "Cleaning up camera preview (closing camera, session, and ImageReader) for cameraId=$cameraId")
            
            try {
                mediaRecorder?.stop()
                mediaRecorder?.reset()
                mediaRecorder?.release()
                if (videoFile != null && videoFile.exists()) {
                    FileLogger.log(context, "CameraPreview", "Video saved successfully to: ${videoFile.absolutePath}")
                    val finalFile = videoFile
                    Thread {
                        exportVideoToGallery(context, finalFile)
                    }.start()
                }
            } catch (e: Exception) {
                FileLogger.log(context, "CameraPreview", "Error stopping MediaRecorder: ${e.message}")
            }

            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
            captureSession = null
            cameraDevice = null
            imageReader = null
            handlerThread.quitSafely()
        }
    }

    // Handles live zoom updates
    LaunchedEffect(zoomLevel, captureSession, previewRequestBuilder, cameraId) {
        val session = captureSession ?: return@LaunchedEffect
        val builder = previewRequestBuilder ?: return@LaunchedEffect
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (activeArraySize != null) {
                val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                val zoom = zoomLevel.coerceIn(1.0f, maxZoom)
                val cropW = (activeArraySize.width() / zoom).toInt()
                val cropH = (activeArraySize.height() / zoom).toInt()
                val deltaW = activeArraySize.width() - cropW
                val deltaH = activeArraySize.height() - cropH
                val cropRect = android.graphics.Rect(
                    deltaW / 2,
                    deltaH / 2,
                    activeArraySize.width() - deltaW / 2,
                    activeArraySize.height() - deltaH / 2
                )
                
                builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
                session.setRepeatingRequest(builder.build(), null, null)
                FileLogger.log(context, "CameraPreview", "Applied live digital zoom: ${zoom}x")
            }
        } catch (e: Exception) {
            FileLogger.log(context, "CameraPreview", "Error applying live zoom: ${e.message}")
            e.printStackTrace()
        }
    }

    // Handles the strobe flash changes on the active capture session to prevent device freezes
    LaunchedEffect(isStrobeActive, captureSession, previewRequestBuilder) {
        val session = captureSession ?: return@LaunchedEffect
        val builder = previewRequestBuilder ?: return@LaunchedEffect

        if (isStrobeActive) {
            var state = false
            while (true) {
                state = !state
                builder.set(CaptureRequest.FLASH_MODE, if (state) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
                try {
                    session.setRepeatingRequest(builder.build(), null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(125) // 4 Hz strobe frequency
            }
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            try {
                session.setRepeatingRequest(builder.build(), null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    textureViewRef = this
                }
            },
            update = { textureView ->
                if (colorMatrix != null) {
                    val paint = Paint().apply {
                        colorFilter = ColorMatrixColorFilter(colorMatrix)
                    }
                    textureView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                } else {
                    textureView.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun chooseOptimalSize(sizes: Array<android.util.Size>, targetRatio: Float, maxPixelCount: Int): android.util.Size {
    var bestMatch = sizes[0]
    var minDiff = Float.MAX_VALUE
    for (size in sizes) {
        if (size.width * size.height > maxPixelCount) continue
        val ratio = size.width.toFloat() / size.height
        val diff = Math.abs(ratio - targetRatio)
        if (diff < minDiff) {
            minDiff = diff
            bestMatch = size
        } else if (diff == minDiff && size.width > bestMatch.width) {
            bestMatch = size
        }
    }
    return bestMatch
}

private fun exportVideoToGallery(context: Context, file: File) {
    try {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(android.provider.MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/WifiGuard")
                put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)

        if (uri != null) {
            resolver.openOutputStream(uri).use { outStream ->
                if (outStream != null) {
                    java.io.FileInputStream(file).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "Saved to Gallery: ${file.name}", Toast.LENGTH_LONG).show()
            }
            FileLogger.log(context, "CameraPreview", "Successfully exported video to Gallery: $uri")
        } else {
            FileLogger.log(context, "CameraPreview", "Failed to insert video entry into MediaStore")
        }
    } catch (e: Exception) {
        FileLogger.log(context, "CameraPreview", "Failed to export video to MediaStore: ${e.message}")
        e.printStackTrace()
    }
}

@Composable
fun AiObjectDetectionView(viewModel: SpyScannerViewModel) {
    val context = LocalContext.current
    val spyState by viewModel.spyState.collectAsState()
    val aiDetectedObject by viewModel.aiDetectedObject.collectAsState()
    val aiConfidence by viewModel.aiConfidence.collectAsState()

    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                Toast.makeText(context, "Camera permission is required for AI Object Scanner.", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        viewModel.setSpyMode(SpyMode.AI_OBJECT)
    }

    val detector = remember { TFLiteObjectDetector(context) }
    DisposableEffect(Unit) {
        onDispose {
            detector.close()
            viewModel.updateAiObject("", 0f)
            viewModel.setSpyMode(SpyMode.OFF)
        }
    }

    var zoomLevel by rememberSaveable { mutableStateOf(1.0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "AI-Powered Camera Object Detection",
            color = Slate50,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Identifies objects (like clocks, wall sockets, smoke detectors, pens, and laptops) that are commonly used as concealment platforms for hidden pinhole cameras.",
            color = Slate400,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Slate700, RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                CameraPreview3(
                    cameraId = spyState.selectedCameraId,
                    colorMatrix = null,
                    isStrobeActive = false,
                    zoomLevel = zoomLevel,
                    isRecording = false,
                    onGlintDetected = { _, _ -> },
                    detector = detector,
                    onObjectDetected = { label, confidence ->
                        viewModel.updateAiObject(label, confidence)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Switch Camera Button
                IconButton(
                    onClick = { viewModel.toggleCamera() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Switch Camera",
                        tint = Slate50,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // AI Detection Label overlay at the bottom
                if (aiDetectedObject.isNotEmpty()) {
                    val lowerLabel = aiDetectedObject.lowercase()
                    val isSuspicious = lowerLabel.contains("socket") || 
                                       lowerLabel.contains("plug") ||
                                       lowerLabel.contains("clock") ||
                                       lowerLabel.contains("watch") ||
                                       lowerLabel.contains("detector") ||
                                       lowerLabel.contains("alarm") ||
                                       lowerLabel.contains("pen") ||
                                       lowerLabel.contains("camera") ||
                                       lowerLabel.contains("microphone") ||
                                       lowerLabel.contains("phone") ||
                                       lowerLabel.contains("television") ||
                                       lowerLabel.contains("keyboard") ||
                                       lowerLabel.contains("laptop")

                    val cleanLabel = aiDetectedObject.split(",").firstOrNull()?.trim() ?: aiDetectedObject

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Detected: ${cleanLabel.uppercase()}",
                            color = Slate50,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "Model Confidence: %.1f%%", aiConfidence * 100),
                            color = Slate400,
                            fontSize = 11.sp
                        )
                        if (isSuspicious) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    lowerLabel.contains("socket") || lowerLabel.contains("plug") -> 
                                        "WARNING: Wall socket USB chargers are common hidden spy camera platforms."
                                    lowerLabel.contains("clock") || lowerLabel.contains("watch") -> 
                                        "WARNING: Digital clocks are frequently hollowed out to embed hidden lens elements."
                                    lowerLabel.contains("detector") || lowerLabel.contains("alarm") -> 
                                        "WARNING: Smoke alarms or wall detectors can easily hide pinhole camera lenses."
                                    lowerLabel.contains("pen") -> 
                                        "WARNING: Portable spy recording pens are common. Inspect carefully."
                                    lowerLabel.contains("camera") -> 
                                        "WARNING: Active Camera Lens detected directly on frame."
                                    lowerLabel.contains("microphone") -> 
                                        "WARNING: Audio recording microphone detected."
                                    else -> 
                                        "WARNING: Common concealment object found. Check surface for pinholes."
                                },
                                color = Red500,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Analyzing frame with local TFLite model...",
                            color = Slate400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .border(1.dp, Slate700, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Camera Permission Required",
                        color = Slate50,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Teal500)
                    ) {
                        Text("Grant Permission", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun configureTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    previewWidth: Int,
    previewHeight: Int,
    context: Context,
    cameraId: String
) {
    if (viewWidth == 0 || viewHeight == 0 || previewWidth == 0 || previewHeight == 0) return
    
    val matrix = android.graphics.Matrix()
    
    // Get window/device display rotation
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.display
        } catch (e: Exception) {
            null
        }
    } else {
        @Suppress("DEPRECATION")
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        wm.defaultDisplay
    }
    val deviceRotation = display?.rotation ?: Surface.ROTATION_0
    
    val viewRect = android.graphics.RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
    val bufferRect = android.graphics.RectF(0f, 0f, previewHeight.toFloat(), previewWidth.toFloat())
    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()
    
    if (Surface.ROTATION_90 == deviceRotation || Surface.ROTATION_270 == deviceRotation) {
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, android.graphics.Matrix.ScaleToFit.FILL)
        
        // Scale to fill the screen (center-crop)
        val scale = Math.max(
            viewHeight.toFloat() / previewHeight,
            viewWidth.toFloat() / previewWidth
        )
        matrix.postScale(scale, scale, centerX, centerY)
        
        // Rotate to match display orientation
        matrix.postRotate((90 * (deviceRotation - 2)).toFloat(), centerX, centerY)
    } else {
        // ROTATION_0 or ROTATION_180
        val scaleX = viewWidth.toFloat() / previewHeight.toFloat()
        val scaleY = viewHeight.toFloat() / previewWidth.toFloat()
        val scale = Math.max(scaleX, scaleY)
        
        matrix.postScale(scale, scale, centerX, centerY)
        
        if (Surface.ROTATION_180 == deviceRotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
    }
    
    textureView.setTransform(matrix)
}

private fun getRotationDegrees(context: Context, cameraId: String): Int {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
    val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    
    // Get window/device display rotation
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.display
        } catch (e: Exception) {
            null
        }
    } else {
        @Suppress("DEPRECATION")
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        wm.defaultDisplay
    }
    
    val deviceRotation = display?.rotation ?: Surface.ROTATION_0
    val degrees = when (deviceRotation) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
    
    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
    return if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
        val sign = (sensorOrientation + degrees) % 360
        (360 - sign) % 360
    } else { // back-facing
        (sensorOrientation - degrees + 360) % 360
    }
}

