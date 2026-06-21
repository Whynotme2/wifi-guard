package com.example.androidnetworkchecker.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    fun log(context: Context, tag: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val logLine = "[$timestamp] [$tag] $message\n"

        // 1. App-specific external documents directory (always works without permission)
        try {
            val appDocsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (appDocsDir != null) {
                if (!appDocsDir.exists()) {
                    appDocsDir.mkdirs()
                }
                val logFile = File(appDocsDir, "wifi_guard_log.txt")
                logFile.appendText(logLine)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Shared Public Documents directory (runs cleanly on Android 10+ via MediaStore without storage permission prompt)
        try {
            val resolver = context.contentResolver
            val uri = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            val relativePath = Environment.DIRECTORY_DOCUMENTS + "/"
            val selectionArgs = arrayOf("wifi_guard_log.txt", relativePath)
            
            var fileUri: android.net.Uri? = null
            resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    fileUri = android.content.ContentUris.withAppendedId(uri, id)
                }
            }

            if (fileUri == null) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "wifi_guard_log.txt")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                }
                fileUri = resolver.insert(uri, contentValues)
            }

            if (fileUri != null) {
                resolver.openOutputStream(fileUri!!, "wa")?.use { output ->
                    output.write(logLine.toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
