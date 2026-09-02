package com.example.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

object StorageHelper {

    /**
     * Mendapatkan OutputStream untuk menyimpan file ke folder Downloads publik
     * menggunakan MediaStore (API 30+).
     */
    fun getOutputStreamForDownload(
        context: Context,
        fileName: String,
        mimeType: String
    ): Pair<OutputStream?, String?> {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        return if (uri != null) {
            resolver.openOutputStream(uri) to uri.toString()
        } else {
            null to null
        }
    }

    /**
     * Memperbarui entry MediaStore setelah unduhan selesai agar terbaca sistem.
     */
    fun finalizeDownload(context: Context, uriString: String?) {
        if (uriString == null) return
        try {
            val uri = Uri.parse(uriString)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, contentValues, null, null)
        } catch (_: Exception) {}
    }
    
}
