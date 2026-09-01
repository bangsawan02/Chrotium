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

    // Initialize full cookie and 3rd-party cookie policy globally
    com.example.engine.CookieHelper.initializeGlobalCookiePolicy()

    // Initialize Safe Browsing API
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      android.webkit.WebView.startSafeBrowsing(this.applicationContext) { success ->
        android.util.Log.i("Chrotium", "Safe Browsing Initialized: $success")
      }
    }

    // Configure ServiceWorkerController to intercept and block background trackers
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val swController = android.webkit.ServiceWorkerController.getInstance()
      val adBlockEngine = com.example.engine.AdBlockEngine(this)
      swController.setServiceWorkerClient(object : android.webkit.ServiceWorkerClient() {
        override fun shouldInterceptRequest(request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
          // Serve from disk cache if possible
          val cachedResponse = com.example.engine.DiskCacheManager.shouldInterceptRequest(request)
          if (cachedResponse != null) return cachedResponse

          // Content blocker for ads and tracking domains in Service Workers
          if (adBlockEngine.shouldBlockRequest(request, null)) {
            return adBlockEngine.createEmptyResourceResponse()
          }
          return super.shouldInterceptRequest(request)
        }
      })
    }

    // Pre-warm DNS & network connections asynchronously
    com.example.engine.WebConfig.warmUpDnsAndNetwork()
    com.example.engine.DiskCacheManager.init(this)

    // Pre-warm the WebView engine on the main thread after the initial layout pass
    window.decorView.post {
      try {
        val dummy = android.webkit.WebView(this)
        dummy.loadUrl("about:blank")
        dummy.destroy()
        android.util.Log.i("Chrotium", "Chromium WebView engine successfully pre-warmed.")
      } catch (e: Exception) {
        // ignore
      }
    }

    // Schedule background periodic maintenance using WorkManager
    val maintenanceWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.worker.BrowserMaintenanceWorker>(
      24, java.util.concurrent.TimeUnit.HOURS
    ).setConstraints(
      androidx.work.Constraints.Builder()
        .setRequiresBatteryNotLow(false)
        .build()
    ).build()

    androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
      "BrowserMaintenanceWork",
      androidx.work.ExistingPeriodicWorkPolicy.KEEP,
      maintenanceWorkRequest
    )

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
        // Triger pembersihan memori global pada engine Chromium WebView
        val dummy = android.webkit.WebView(this)
        @Suppress("DEPRECATION")
        dummy.freeMemory()
        dummy.destroy()
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



