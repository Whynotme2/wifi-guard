package com.example.androidnetworkchecker.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidnetworkchecker.data.DataRepository
import com.example.androidnetworkchecker.data.NetworkState
import com.example.androidnetworkchecker.data.VulnerabilityReport
import com.example.androidnetworkchecker.data.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    fun startVulnerabilityAudit(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.performVulnerabilityAudit(context)
        }
    }

    fun clearVulnerabilityReport() {
        dataRepository.clearVulnerabilityReport()
    }

    fun exportVulnerabilityReportToPdf(context: Context) {
        val report = uiState.value.vulnerabilityState.report ?: run {
            Toast.makeText(context, "No vulnerability report available to export", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Set up paints
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 12f
                    isAntiAlias = true
                }
                
                val titlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 18f
                    isFakeBoldText = true
                    isAntiAlias = true
                }

                val headerBgPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#0F172A") // Slate900
                    style = android.graphics.Paint.Style.FILL
                }

                val tealPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#14B8A6") // Teal500
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }

                val lightGrayBgPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#F1F5F9") // Slate100
                    style = android.graphics.Paint.Style.FILL
                }

                val borderPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#CBD5E1") // Slate300
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1f
                }

                val successPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#10B981") // Emerald500
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }

                val warningPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#F59E0B") // Amber500
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }

                val dangerPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#EF4444") // Red500
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }

                // 1. Draw Header Bar
                canvas.drawRect(0f, 0f, 595f, 90f, headerBgPaint)
                canvas.drawRect(0f, 90f, 595f, 95f, tealPaint)

                // Header Text
                canvas.drawText("WIFI GUARD SECURITY SUITE", 30f, 40f, titlePaint)
                val subtitlePaint = android.graphics.Paint(titlePaint).apply {
                    textSize = 11f
                    color = android.graphics.Color.parseColor("#94A3B8") // Slate400
                    isFakeBoldText = false
                }
                canvas.drawText("AUTOMATED NETWORK VULNERABILITY AUDIT CERTIFICATE", 30f, 65f, subtitlePaint)

                // 2. Draw Metadata Block
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                val scanDate = sdf.format(java.util.Date(report.timestamp))
                
                // Draw rounded box for metadata
                val metaRect = android.graphics.RectF(30f, 115f, 565f, 185f)
                canvas.drawRoundRect(metaRect, 8f, 8f, lightGrayBgPaint)
                canvas.drawRoundRect(metaRect, 8f, 8f, borderPaint)

                val metaLabelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#475569") // Slate600
                    textSize = 10f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val metaValuePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#0F172A") // Slate900
                    textSize = 10f
                    isAntiAlias = true
                }

                canvas.drawText("SSID:", 50f, 137f, metaLabelPaint)
                canvas.drawText(report.ssid, 140f, 137f, metaValuePaint)

                canvas.drawText("SECURITY:", 50f, 162f, metaLabelPaint)
                canvas.drawText(report.securityType, 140f, 162f, metaValuePaint)

                canvas.drawText("SCAN TIME:", 310f, 137f, metaLabelPaint)
                canvas.drawText(scanDate, 400f, 137f, metaValuePaint)

                canvas.drawText("RISK SCORE:", 310f, 162f, metaLabelPaint)
                canvas.drawText("${report.riskScore} / 100", 400f, 162f, metaValuePaint)

                // 3. Draw Risk Gauge
                val sectionTitlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#0F172A") // Slate900
                    textSize = 13f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                
                canvas.drawText("OVERALL THREAT ASSESSMENT", 30f, 215f, sectionTitlePaint)

                // Draw linear color bar for risk score
                val barLeft = 30f
                val barRight = 565f
                val barTop = 230f
                val barBottom = 245f
                
                // Segments: Safe (0-15: Green), Low/Medium (16-60: Orange), High (61-100: Red)
                // Width = 535
                val greenWidth = 535f * 0.15f // 80.25
                val orangeWidth = 535f * 0.45f // 240.75
                
                canvas.drawRect(barLeft, barTop, barLeft + greenWidth, barBottom, successPaint)
                canvas.drawRect(barLeft + greenWidth, barTop, barLeft + greenWidth + orangeWidth, barBottom, warningPaint)
                canvas.drawRect(barLeft + greenWidth + orangeWidth, barTop, barRight, barBottom, dangerPaint)

                // Draw threat pointer
                val pointerX = barLeft + (report.riskScore / 100f) * 535f
                val pointerPath = android.graphics.Path().apply {
                    moveTo(pointerX, barBottom + 2f)
                    lineTo(pointerX - 6f, barBottom + 10f)
                    lineTo(pointerX + 6f, barBottom + 10f)
                    close()
                }
                val pointerPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#0F172A")
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawPath(pointerPath, pointerPaint)

                // Score text overlay
                val ratingPaint = android.graphics.Paint().apply {
                    color = when (report.riskRating) {
                        "Safe" -> android.graphics.Color.parseColor("#10B981")
                        "Low Risk", "Medium Risk" -> android.graphics.Color.parseColor("#D97706")
                        else -> android.graphics.Color.parseColor("#EF4444")
                    }
                    textSize = 14f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                canvas.drawText(report.riskRating.uppercase(), pointerX - 25f, barBottom + 25f, ratingPaint)

                // 4. Security Checklist Checks
                canvas.drawText("DIAGNOSTIC CHECKLIST", 30f, 310f, sectionTitlePaint)

                var yOffset = 340f
                
                fun drawCheckRow(label: String, detail: String, isPass: Boolean) {
                    // Draw indicator Circle
                    val indicatorPaint = if (isPass) successPaint else dangerPaint
                    canvas.drawCircle(45f, yOffset - 4f, 8f, indicatorPaint)
                    
                    // Draw Check or X inside circle
                    val symbolPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        strokeWidth = 1.5f
                        style = android.graphics.Paint.Style.STROKE
                        isAntiAlias = true
                    }
                    if (isPass) {
                        // Checkmark lines
                        canvas.drawLine(41f, yOffset - 4f, 44f, yOffset - 1f, symbolPaint)
                        canvas.drawLine(44f, yOffset - 1f, 49f, yOffset - 7f, symbolPaint)
                    } else {
                        // X lines
                        canvas.drawLine(41f, yOffset - 8f, 49f, yOffset, symbolPaint)
                        canvas.drawLine(49f, yOffset - 8f, 41f, yOffset, symbolPaint)
                    }

                    val rowLabelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#0F172A")
                        textSize = 10f
                        isFakeBoldText = true
                        isAntiAlias = true
                    }
                    val rowValuePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#475569")
                        textSize = 10f
                        isAntiAlias = true
                    }

                    canvas.drawText(label, 70f, yOffset, rowLabelPaint)
                    canvas.drawText(detail, 250f, yOffset, rowValuePaint)
                    
                    // Thin divider line
                    canvas.drawLine(30f, yOffset + 12f, 565f, yOffset + 12f, borderPaint)
                    
                    yOffset += 32f
                }

                // Check 1: Encryption
                drawCheckRow(
                    "Wi-Fi Encryption",
                    if (report.isEncrypted) "PASSED - ${report.securityType}" else "FAILED - Open Network, no encryption",
                    report.isEncrypted
                )

                // Check 2: Exposed gateway ports
                drawCheckRow(
                    "Exposed Admin Ports",
                    if (report.openPortsCount == 0) "PASSED - No standard admin ports open" else "WARNING - Open ports: ${report.openPortsList.joinToString(", ")}",
                    report.openPortsCount == 0
                )

                // Check 3: DNS Leak
                drawCheckRow(
                    "DNS Leak Verification",
                    if (!report.dnsLeakDetected) "PASSED - No DNS leaks detected" else "WARNING - DNS leak active, VPN bypass",
                    !report.dnsLeakDetected
                )

                // Check 4: DNS Redundancy
                drawCheckRow(
                    "DNS Redundancy",
                    "PASSED - ${report.dnsServersCount} DNS servers configured",
                    report.dnsServersCount > 1
                )

                // 5. Actionable Recommendations
                canvas.drawText("ACTIONABLE RECOMMENDATIONS", 30f, 490f, sectionTitlePaint)

                var recY = 515f
                val bulletPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#64748B")
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }
                val recTextPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#1E293B")
                    textSize = 9.5f
                    isAntiAlias = true
                }

                for (rec in report.recommendations) {
                    canvas.drawCircle(45f, recY - 3f, 3f, bulletPaint)
                    
                    // Multi-line wrap helper for long recommendations
                    val words = rec.split(" ")
                    var line = ""
                    val maxWidth = 480f
                    var firstLine = true
                    for (word in words) {
                        val testLine = if (line.isEmpty()) word else "$line $word"
                        val testWidth = recTextPaint.measureText(testLine)
                        if (testWidth > maxWidth) {
                            canvas.drawText(line, 60f, recY, recTextPaint)
                            line = word
                            recY += 14f
                            firstLine = false
                        } else {
                            line = testLine
                        }
                    }
                    if (line.isNotEmpty()) {
                        canvas.drawText(line, 60f, recY, recTextPaint)
                    }
                    recY += 22f
                }

                // 6. Draw Footer Separator & Info
                canvas.drawLine(30f, 790f, 565f, 790f, borderPaint)
                val footerPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#94A3B8")
                    textSize = 8f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.drawText("WifiGuard Security Suite • Generated locally on device • 100% Offline & Private", 297.5f, 810f, footerPaint)

                pdfDocument.finishPage(page)

                // Save to private document storage for sharing
                val tempFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "wifi_guard_audit_report.pdf")
                FileOutputStream(tempFile).use { out ->
                    pdfDocument.writeTo(out)
                }

                // Save to public Downloads directory
                savePdfToPublicDownloads(context, pdfDocument)

                pdfDocument.close()

                // Launch Intent to share PDF file
                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, "WifiGuard Network Security Audit Report")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Share Security Report PDF")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)

                FileLogger.log(context, "VulnerabilityReport", "Successfully exported PDF report to public downloads and launched sharing sheet")
            } catch (e: Exception) {
                FileLogger.log(context, "VulnerabilityReport", "Error generating PDF report: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun savePdfToPublicDownloads(context: Context, pdfDocument: android.graphics.pdf.PdfDocument) {
        val filename = "wifi_guard_audit_${System.currentTimeMillis()}.pdf"
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/WifiGuard")
            }
        }
        
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val wifiGuardDir = File(downloadDir, "WifiGuard")
                if (!wifiGuardDir.exists()) wifiGuardDir.mkdirs()
                val file = File(wifiGuardDir, filename)
                FileOutputStream(file).use { out ->
                    pdfDocument.writeTo(out)
                }
                null
            }

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
            }
            
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "PDF Report saved to Downloads/WifiGuard/$filename", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save PDF locally: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
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
