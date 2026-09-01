package com.example.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

object StorageHelper {

    /**
     * Mendapatkan OutputStream untuk menyimpan file ke folder Downloads publik
     * menggunakan MediaStore (API 29+) atau File API (API < 29).
     */
    fun getOutputStreamForDownload(
        context: Context,
        fileName: String,
        mimeType: String
    ): Pair<OutputStream?, String?> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri) to uri.toString()
            } else {
                null to null
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            file.outputStream() to file.absolutePath
        }
    }

    /**
     * Memperbarui entry MediaStore setelah unduhan selesai agar terbaca sistem (API 29+).
     */
    fun finalizeDownload(context: Context, uriString: String?) {
        if (uriString == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val uri = Uri.parse(uriString)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, contentValues, null, null)
        } catch (e: Exception) {
            // ignore
        }
    }
    
    /**
     * Menghapus file unduhan (API 29+ via Uri, API < 29 via Path)
     */
    fun deleteDownload(context: Context, uriString: String?, filePath: String) {
        try {
            if (uriString != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.delete(Uri.parse(uriString), null, null)
            }
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
