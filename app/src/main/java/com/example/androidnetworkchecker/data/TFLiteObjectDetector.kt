package com.example.androidnetworkchecker.data

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class TFLiteObjectDetector(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val labels = mutableListOf<String>()

    data class DetectedObject(
        val label: String,
        val confidence: Float,
        val boundingBox: android.graphics.RectF // [left, top, right, bottom] (0..1)
    )

    init {
        try {
            // Load SSD TFLite model from assets
            val fileDescriptor = context.assets.openFd("ssd_mobilenet.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            
            interpreter = Interpreter(modelBuffer)
            
            // Load COCO labels from assets
            context.assets.open("ssd_labels.txt").bufferedReader().useLines { lines ->
                labels.addAll(lines)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLabels(): List<String> = labels

    fun classifyFrame(image: android.media.Image, rotationDegrees: Int): List<DetectedObject> {
        val interp = interpreter ?: return emptyList()
        if (labels.isEmpty()) return emptyList()

        val inputBuffer = ByteBuffer.allocateDirect(300 * 300 * 3).apply {
            order(ByteOrder.nativeOrder())
        }

        try {
            // Convert YUV to 300x300 RGB with rotation
            convertYuvToByteBuffer(image, inputBuffer, rotationDegrees)
            inputBuffer.rewind()

            // SSD outputs
            // Output locations: [1, 10, 4]
            val outputLocations = Array(1) { Array(10) { FloatArray(4) } }
            // Output classes: [1, 10]
            val outputClasses = Array(1) { FloatArray(10) }
            // Output scores: [1, 10]
            val outputScores = Array(1) { FloatArray(10) }
            // Output num detections: [1]
            val numDetections = FloatArray(1)

            val outputs = HashMap<Int, Any>()
            outputs[0] = outputLocations
            outputs[1] = outputClasses
            outputs[2] = outputScores
            outputs[3] = numDetections

            interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

            val count = numDetections[0].toInt().coerceAtMost(10)
            val result = mutableListOf<DetectedObject>()
            for (i in 0 until count) {
                val score = outputScores[0][i]
                if (score >= 0.35f) {
                    // SSD COCO classes are 0-indexed in output but map to 1-indexed in labels file (since index 0 is '???')
                    val classIndex = outputClasses[0][i].toInt() + 1
                    if (classIndex in labels.indices) {
                        val label = labels[classIndex]
                        val loc = outputLocations[0][i] // [ymin, xmin, ymax, xmax]
                        // RectF bounding box format: [left, top, right, bottom]
                        val box = android.graphics.RectF(loc[1], loc[0], loc[3], loc[2])
                        result.add(DetectedObject(label, score, box))
                    }
                }
            }
            return result
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    // High performance localized downsampling during YUV to RGB conversion with rotation alignment
    private fun convertYuvToByteBuffer(image: android.media.Image, byteBuffer: ByteBuffer, rotationDegrees: Int) {
        byteBuffer.rewind()
        val w = image.width
        val h = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        
        val yStride = yPlane.rowStride
        val uvStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        
        val wMinus1 = (w - 1).toFloat()
        val hMinus1 = (h - 1).toFloat()
        
        for (outY in 0 until 300) {
            val v = outY.toFloat() / 299f
            for (outX in 0 until 300) {
                val u = outX.toFloat() / 299f
                
                // Map output (u, v) coordinates back to source (srcX, srcY) depending on rotation degrees
                val srcX: Int
                val srcY: Int
                when (rotationDegrees) {
                    90 -> {
                        srcX = (v * wMinus1).toInt().coerceIn(0, w - 1)
                        srcY = ((1.0f - u) * hMinus1).toInt().coerceIn(0, h - 1)
                    }
                    180 -> {
                        srcX = ((1.0f - u) * wMinus1).toInt().coerceIn(0, w - 1)
                        srcY = ((1.0f - v) * hMinus1).toInt().coerceIn(0, h - 1)
                    }
                    270 -> {
                        srcX = ((1.0f - v) * wMinus1).toInt().coerceIn(0, w - 1)
                        srcY = (u * hMinus1).toInt().coerceIn(0, h - 1)
                    }
                    else -> { // 0 or standard landscape
                        srcX = (u * wMinus1).toInt().coerceIn(0, w - 1)
                        srcY = (v * hMinus1).toInt().coerceIn(0, h - 1)
                    }
                }
                
                val yIndex = srcY * yStride + srcX
                val uIndex = (srcY shr 1) * uvStride + (srcX shr 1) * uvPixelStride
                val vIndex = (srcY shr 1) * uvStride + (srcX shr 1) * uvPixelStride
                
                if (yIndex < yBuffer.remaining() && uIndex < uBuffer.remaining() && vIndex < vBuffer.remaining()) {
                    val yp = yBuffer.get(yIndex).toInt() and 0xFF
                    val up = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
                    val vp = (vBuffer.get(vIndex).toInt() and 0xFF) - 128
                    
                    var r = (yp + 1.402f * vp).toInt()
                    var g = (yp - 0.344f * up - 0.714f * vp).toInt()
                    var b = (yp + 1.772f * up).toInt()
                    
                    r = r.coerceIn(0, 255)
                    g = g.coerceIn(0, 255)
                    b = b.coerceIn(0, 255)
                    
                    byteBuffer.put(r.toByte())
                    byteBuffer.put(g.toByte())
                    byteBuffer.put(b.toByte())
                } else {
                    byteBuffer.put(0.toByte())
                    byteBuffer.put(0.toByte())
                    byteBuffer.put(0.toByte())
                }
            }
        }
    }
}
