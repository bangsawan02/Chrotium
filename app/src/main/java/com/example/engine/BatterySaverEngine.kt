package com.example.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class BatteryStatus(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val isSystemPowerSave: Boolean = false,
    val minutesActiveInSession: Long = 0L
)

class BatterySaverEngine(private val context: Context) {

    private val _status = MutableStateFlow(BatteryStatus())
    val status: StateFlow<BatteryStatus> = _status.asStateFlow()

    private val sessionStartTime = System.currentTimeMillis()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent?.let { updateBatteryFromIntent(it) }
        }
    }

    init {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val initialIntent = androidx.core.content.ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            initialIntent?.let { updateBatteryFromIntent(it) }
        } catch (e: Exception) {
            try {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val initialIntent = context.registerReceiver(batteryReceiver, filter)
                initialIntent?.let { updateBatteryFromIntent(it) }
            } catch (e2: Exception) {
                // Last resort: ignore battery status if receiver fails
            }
        }
    }

    private fun updateBatteryFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val statusVal = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = statusVal == BatteryManager.BATTERY_STATUS_CHARGING || statusVal == BatteryManager.BATTERY_STATUS_FULL

        val batteryPct = if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100).roundToInt()
        } else {
            100
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode ?: false

        val minutesElapsed = ((System.currentTimeMillis() - sessionStartTime) / 60000L).coerceAtLeast(0L)

        val currentStatus = _status.value
        _status.value = currentStatus.copy(
            levelPercent = batteryPct,
            isCharging = isCharging,
            isSystemPowerSave = isPowerSave,
            minutesActiveInSession = minutesElapsed
        )
    }

    /**
     * Konfigurasi WebSettings untuk efisiensi energi dan kinerja optimal
     */
    fun configureWebSettings(settings: WebSettings, isDarkTheme: Boolean) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // databaseEnabled is deprecated and WebSQL is removed in modern Chrome
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        
        // Modern websites & Video rendering capabilities
        settings.setGeolocationEnabled(true)
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.safeBrowsingEnabled = true
        
        try {
            @Suppress("DEPRECATION")
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK)) {
                androidx.webkit.WebSettingsCompat.setForceDark(
                    settings,
                    if (isDarkTheme) androidx.webkit.WebSettingsCompat.FORCE_DARK_ON else androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
                )
            }
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
                androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDarkTheme)
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                settings.isAlgorithmicDarkeningAllowed = isDarkTheme
            }
        } catch (e: Throwable) {
            // fallback aman
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }
}
