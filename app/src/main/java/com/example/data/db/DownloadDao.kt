package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloadsSync(): List<DownloadItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadItem): Long

    @Update
    suspend fun updateDownload(item: DownloadItem)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes WHERE downloadId = :downloadId")
    suspend fun updateDownloadProgress(downloadId: Long, status: Int, downloadedBytes: Long, totalBytes: Long)

    @Query("UPDATE downloads SET status = :status, fileUri = :fileUri WHERE downloadId = :downloadId")
    suspend fun updateDownloadStatusWithUri(downloadId: Long, status: Int, fileUri: String)

    @Delete
    suspend fun deleteDownload(item: DownloadItem)

    @Query("DELETE FROM downloads")
    suspend fun clearAllDownloads()
}
