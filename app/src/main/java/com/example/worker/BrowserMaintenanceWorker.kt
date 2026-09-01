package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.engine.DiskCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BrowserMaintenanceWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i("BrowserMaintenance", "Starting background maintenance work...")

            // Initialize cache manager
            DiskCacheManager.init(applicationContext)

            // Prune cache if cache size exceeds 50MB
            val currentCacheSize = DiskCacheManager.getCacheSizeMb()
            Log.i("BrowserMaintenance", "Current cache size: $currentCacheSize MB")

            if (currentCacheSize > 50.0f) {
                val cacheDir = File(applicationContext.cacheDir, "static_web_cache")
                if (cacheDir.exists()) {
                    val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                    val files = cacheDir.listFiles()
                    files?.filter { file -> file.isFile && file.lastModified() < sevenDaysAgo }
                        ?.forEach { oldFile ->
                            oldFile.delete()
                        }
                }
            }

            Log.i("BrowserMaintenance", "Background maintenance work completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("BrowserMaintenance", "Error performing maintenance work", e)
            Result.failure()
        }
    }
}
