package com.example.worker

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.example.engine.DiskCacheManager
import java.io.File
import kotlin.concurrent.thread

class BrowserMaintenanceJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        thread(start = true) {
            try {
                Log.i("BrowserMaintenance", "Starting background maintenance work...")
                DiskCacheManager.init(applicationContext)
                com.example.engine.WebViewCacheHelper.ensureCacheDirectories(applicationContext)

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
                jobFinished(params, false)
            } catch (e: Exception) {
                Log.e("BrowserMaintenance", "Error performing maintenance work", e)
                jobFinished(params, true)
            }
        }
        return true // Task is running in background thread
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // Retry the job
    }
}
