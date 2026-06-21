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

    init {
        try {
            // Load TFLite model from assets
            val fileDescriptor = context.assets.openFd("mobilenet.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            
            interpreter = Interpreter(modelBuffer)
            
            // Load labels from assets
            context.assets.open("labels.txt").bufferedReader().useLines { lines ->
                labels.addAll(lines)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLabels(): List<String> = labels

    fun classifyFrame(image: android.media.Image, rotationDegrees: Int): Pair<String, Float>? {
        val interp = interpreter ?: return null
        if (labels.isEmpty()) return null

        val inputBuffer = ByteBuffer.allocateDirect(224 * 224 * 3).apply {
            order(ByteOrder.nativeOrder())
        }

        try {
            // Convert YUV to 224x224 RGB with rotation
            convertYuvToByteBuffer(image, inputBuffer, rotationDegrees)
            inputBuffer.rewind()

            // Run inference (quantized outputs)
            val outputArray = Array(1) { ByteArray(labels.size) }
            interp.run(inputBuffer, outputArray)

            // Find highest confidence class
            var maxVal = -1
            val outputVal = outputArray[0]
            var maxIndex = -1
            for (i in outputVal.indices) {
                val value = outputVal[i].toInt() and 0xFF
                if (value > maxVal) {
                    maxVal = value
                    maxIndex = i
                }
            }

            if (maxIndex in labels.indices) {
                val label = labels[maxIndex]
                val confidence = maxVal / 255.0f
                if (confidence >= 0.35f) {
                    return Pair(label, confidence)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
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
        
        for (outY in 0 until 224) {
            val v = outY.toFloat() / 223f
            for (outX in 0 until 224) {
                val u = outX.toFloat() / 223f
                
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
