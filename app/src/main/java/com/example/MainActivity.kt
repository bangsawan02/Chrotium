package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.BrowserMainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    
    // Request permission for showing notifications in Android 13+ (API 33)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
    }

    // Explicitly enable hardware acceleration for smooth video playback and UI rendering
    window.setFlags(
      android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
      android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    )

    // Maximize display refresh rate (90Hz / 120Hz / 144Hz) for highest rendering fluidity
    try {
      val display = this.display
      val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
      if (maxMode != null && maxMode.refreshRate > 60f) {
        val lp = window.attributes
        lp.preferredDisplayModeId = maxMode.modeId
        window.attributes = lp
      }
    } catch (_: Exception) {}

    // Initialize full cookie and 3rd-party cookie policy globally
    com.example.engine.CookieHelper.initializeGlobalCookiePolicy()

    // Initialize Safe Browsing API
    android.webkit.WebView.startSafeBrowsing(this.applicationContext) { success ->
      android.util.Log.i("Chrotium", "Safe Browsing Initialized: $success")
    }

    // Configure ServiceWorkerController to intercept and block background trackers
    val swController = android.webkit.ServiceWorkerController.getInstance()
    val adBlockEngine = com.example.engine.AdBlockEngine(this)
    swController.setServiceWorkerClient(object : android.webkit.ServiceWorkerClient() {
      override fun shouldInterceptRequest(request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
        // Content blocker for ads and tracking domains in Service Workers
        if (adBlockEngine.shouldBlockRequest(request, null)) {
          return adBlockEngine.createEmptyResourceResponse()
        }
        return super.shouldInterceptRequest(request)
      }
    })

    // Ensure all Chromium WebView cache & code cache directories exist to prevent SimpleCache ENOENT index errors
    com.example.engine.WebViewCacheHelper.ensureCacheDirectories(this)

    // Pre-warm DNS & network connections asynchronously
    com.example.engine.WebConfig.warmUpDnsAndNetwork()
    com.example.engine.DiskCacheManager.init(this)

    // Schedule background periodic maintenance using JobScheduler
    val jobScheduler = getSystemService(android.content.Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
    val jobInfo = android.app.job.JobInfo.Builder(
        1001,
        android.content.ComponentName(this, com.example.worker.BrowserMaintenanceJobService::class.java)
    )
    .setPeriodic(24 * 60 * 60 * 1000L) // 24 hours
    .setRequiresBatteryNotLow(false)
    .setPersisted(true)
    .build()

    jobScheduler.schedule(jobInfo)

    setContent {
      MyApplicationTheme {
        BrowserMainScreen(modifier = Modifier.fillMaxSize())
      }
    }
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
      try {
        com.example.engine.WebViewCacheHelper.ensureCacheDirectories(this)
      } catch (e: Exception) {
        // safe ignore
      }
    }
  }

  override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
    super.onConfigurationChanged(newConfig)
    android.util.Log.i("MainActivity", "Configuration changed (theme/orientation). Activity saved from restart.")
  }
}



