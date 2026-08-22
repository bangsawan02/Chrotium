package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val downloadId: Long = -1,
    val fileName: String,
    val fileUrl: String,
    val mimeType: String,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: Int = 0, // 0: Menunggu/Berjalan, 1: Selesai, 2: Gagal
    val filePath: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
