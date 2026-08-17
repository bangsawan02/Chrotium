package com.example.engine

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.util.Collections

data class AdBlockStats(
    val isEnabled: Boolean = true,
    val isCosmeticFilteringEnabled: Boolean = true,
    val isTrackerBlockingEnabled: Boolean = true,
    val totalBlockedCount: Long = 0L,
    val savedDataKbEstimate: Long = 0L,
    val whitelistedDomains: Set<String> = emptySet()
)

class AdBlockEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("crotium_adblock_prefs", Context.MODE_PRIVATE)

    private val _stats = MutableStateFlow(
        AdBlockStats(
            isEnabled = prefs.getBoolean("adblock_enabled", true),
            isCosmeticFilteringEnabled = prefs.getBoolean("cosmetic_enabled", true),
            isTrackerBlockingEnabled = prefs.getBoolean("trackers_enabled", true),
            totalBlockedCount = prefs.getLong("total_blocked_count", 0L),
            savedDataKbEstimate = prefs.getLong("saved_data_kb", 0L),
            whitelistedDomains = prefs.getStringSet("whitelisted_domains", emptySet()) ?: emptySet()
        )
    )
    val stats: StateFlow<AdBlockStats> = _stats.asStateFlow()

    // Host-based blocklist (ad networks, exchanges, trackers, telemetry, miners, intrusive popups)
    private val blockedDomainSuffixes: Set<String> = hashSetOf(
        // Google Ad & Tracker ecosystem
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "google-analytics.com",
        "analytics.google.com",
        "googletagmanager.com",
        "googletagservices.com",
        "stats.g.doubleclick.net",
        "ad.doubleclick.net",
        "adclick.g.doubleclick.net",

        // Major Mobile & Web Ad Networks
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "criteo.net",
        "adnxs.com",
        "amazon-adsystem.com",
        "media.net",
        "pubmatic.com",
        "openx.net",
        "rubiconproject.com",
        "casalemedia.com",
        "applovin.com",
        "unityads.unity3d.com",
        "unityads.com",
        "vungle.com",
        "inmobi.com",
        "smaato.net",
        "adcolony.com",
        "chartboost.com",
        "ironsrc.com",
        "supersonicads.com",
        "admob.com",
        "mobfox.com",
        "leadbolt.com",
        "startapp.com",
        "startappservice.com",
        "tapjoy.com",
        "mopub.com",
        "smartadserver.com",
        "yieldmo.com",
        "adroll.com",
        "adroll.net",
        "revcontent.com",
        "mgid.com",
        "zergnet.com",
        "bidvertiser.com",
        "adsterra.com",
        "propellerads.com",
        "popads.net",
        "popcash.net",
        "exoclick.com",
        "trafficjunky.com",
        "trafficfactory.biz",
        "clickadu.com",
        "richpush.co",
        "monetag.com",
        "hilltopads.com",
        "ero-advertising.com",
        "juicyads.com",
        "plugrush.com",

        // Trackers, Telemetry & User Fingerprinting
        "hotjar.com",
        "yandex.ru/metrika",
        "mc.yandex.ru",
        "facebook.net/en_US/fbevents.js",
        "connect.facebook.net",
        "pixel.facebook.com",
        "an.facebook.com",
        "tr.snapchat.com",
        "analytics.tiktok.com",
        "ads.twitter.com",
        "t.co/i/adsct",
        "segment.io",
        "segment.com",
        "mixpanel.com",
        "amplitude.com",
        "branch.io",
        "appsflyer.com",
        "adjust.com",
        "kochava.com",
        "scorecardresearch.com",
        "quantserve.com",
        "chartbeat.com",
        "optimizely.com",
        "crazyegg.com",
        "mouseflow.com",
        "clicky.com",
        "statcounter.com",
        "histats.com",
        "gemius.pl",
        "clarity.ms",

        // Cryptominers & malicious scripts
        "coinhive.com",
        "crypto-loot.com",
        "webminepool.com",
        "jsecoin.com",
        "minr.pw",
        "coin-have.com",
        "cryptoloot.pro"
    )

    // Path and Keyword block patterns combined into a single optimized Regex
    private val blockedUrlRegex: Regex = Regex(
        """.*[/._](ads?|advert(ising|isement)?|ad_service|popunder|banner_ad|pagead|telemetry|tracker|fbevents\.js|gtag/js\?id=|analytics\.js).*""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Memeriksa apakah request jaringan harus diblokir
     */
    fun shouldBlockRequest(request: WebResourceRequest, pageUrl: String?): Boolean {
        val currentStats = _stats.value
        if (!currentStats.isEnabled) return false

        val uri = request.url ?: return false
        val host = uri.host?.lowercase() ?: return false
        val fullUrl = uri.toString()

        // Periksa apakah domain induk atau domain aktif ada di daftar pengecualian (whitelist)
        if (isDomainWhitelisted(pageUrl) || isDomainWhitelisted(host)) {
            return false
        }

        // 1. Host-based blocklist check
        if (isHostBlocked(host)) {
            recordBlockedRequest(fullUrl)
            return true
        }

        // 2. Path & Pattern matching check
        if (currentStats.isTrackerBlockingEnabled) {
            if (blockedUrlRegex.matches(fullUrl)) {
                recordBlockedRequest(fullUrl)
                return true
            }
        }

        return false
    }

    private fun isHostBlocked(host: String): Boolean {
        var domain = host
        while (domain.contains('.')) {
            if (blockedDomainSuffixes.contains(domain)) {
                return true
            }
            val dotIndex = domain.indexOf('.')
            if (dotIndex == -1 || dotIndex == domain.length - 1) break
            domain = domain.substring(dotIndex + 1)
        }
        return blockedDomainSuffixes.contains(domain)
    }

    fun isDomainWhitelisted(urlOrDomain: String?): Boolean {
        if (urlOrDomain.isNullOrBlank()) return false
        val host = try {
            if (urlOrDomain.startsWith("http://") || urlOrDomain.startsWith("https://")) {
                Uri.parse(urlOrDomain).host?.lowercase() ?: urlOrDomain.lowercase()
            } else {
                urlOrDomain.lowercase()
            }
        } catch (e: Exception) {
            urlOrDomain.lowercase()
        }

        val whitelisted = _stats.value.whitelistedDomains
        return whitelisted.any { host == it || host.endsWith(".$it") }
    }

    fun toggleDomainWhitelist(urlOrDomain: String) {
        val host = try {
            if (urlOrDomain.startsWith("http://") || urlOrDomain.startsWith("https://")) {
                Uri.parse(urlOrDomain).host?.lowercase() ?: urlOrDomain.lowercase()
            } else {
                urlOrDomain.lowercase()
            }
        } catch (e: Exception) {
            urlOrDomain.lowercase()
        }
        if (host.isBlank()) return

        val currentWhitelist = _stats.value.whitelistedDomains.toMutableSet()
        if (currentWhitelist.contains(host)) {
            currentWhitelist.remove(host)
        } else {
            currentWhitelist.add(host)
        }

        prefs.edit().putStringSet("whitelisted_domains", currentWhitelist).apply()
        _stats.value = _stats.value.copy(whitelistedDomains = currentWhitelist)
    }

    private fun recordBlockedRequest(url: String) {
        val currentBlocked = _stats.value.totalBlockedCount + 1
        // Estimasi rata-rata ukuran aset iklan/skrip pihak ketiga ~45 KB
        val currentSavedKb = _stats.value.savedDataKbEstimate + 45

        _stats.value = _stats.value.copy(
            totalBlockedCount = currentBlocked,
            savedDataKbEstimate = currentSavedKb
        )

        // Simpan secara periodik ke SharedPreferences
        if (currentBlocked % 5L == 0L) {
            prefs.edit()
                .putLong("total_blocked_count", currentBlocked)
                .putLong("saved_data_kb", currentSavedKb)
                .apply()
        }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("adblock_enabled", enabled).apply()
        _stats.value = _stats.value.copy(isEnabled = enabled)
    }

    fun setCosmeticFilteringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("cosmetic_enabled", enabled).apply()
        _stats.value = _stats.value.copy(isCosmeticFilteringEnabled = enabled)
    }

    fun setTrackerBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("trackers_enabled", enabled).apply()
        _stats.value = _stats.value.copy(isTrackerBlockingEnabled = enabled)
    }

    fun resetStats() {
        prefs.edit()
            .putLong("total_blocked_count", 0L)
            .putLong("saved_data_kb", 0L)
            .apply()
        _stats.value = _stats.value.copy(totalBlockedCount = 0L, savedDataKbEstimate = 0L)
    }

    /**
     * Respon kosong berkecepatan tinggi untuk menolak permintaan jaringan iklan
     */
    fun createEmptyResourceResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            Collections.singletonMap("Access-Control-Allow-Origin", "*"),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * CSS Cosmetic element hiding untuk meruntuhkan ruang iklan di halaman web
     */
    val COSMETIC_AD_BLOCK_CSS: String = """
        ins.adsbygoogle,
        .adsbygoogle,
        [id^="google_ads_"],
        [id*="google_ads_iframe"],
        [class*="sponsored-post"],
        [class*="native-ad"],
        [class*="ad-banner"],
        [class*="ad-container"],
        [class*="ad-wrapper"],
        [class*="ad-slot"],
        [class*="ad_unit"],
        [id*="banner-ad"],
        [id*="taboola-"],
        [id*="outbrain_"],
        .trc_related_container,
        .mgbox,
        .ad-placement,
        .advertisement,
        .ad_top,
        .ad_bottom,
        .ad_sidebar,
        iframe[src*="doubleclick.net"],
        iframe[src*="googlesyndication.com"],
        iframe[src*="adservice.google.com"],
        iframe[src*="adnxs.com"],
        iframe[src*="taboola.com"] {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            min-height: 0 !important;
            max-height: 0 !important;
            width: 0 !important;
            min-width: 0 !important;
            max-width: 0 !important;
            opacity: 0 !important;
            pointer-events: none !important;
            margin: 0 !important;
            padding: 0 !important;
        }
    """.trimIndent()

    /**
     * Pre-computed JavaScript injection string for cosmetic filtering
     */
    private val cachedCosmeticJs: String by lazy {
        val minifiedCss = COSMETIC_AD_BLOCK_CSS.replace("\n", "").replace("'", "\\'")
        """
        (function() {
            try {
                var styleId = 'crotium-adblock-cosmetic-style';
                if (!document.getElementById(styleId)) {
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.textContent = '$minifiedCss';
                    (document.head || document.documentElement || document.body).appendChild(style);
                }
            } catch(e) {}
        })();
        """.trimIndent()
    }

    /**
     * Injeksi CSS Cosmetic Ad-Hiding ke WebView
     */
    fun injectCosmeticAdBlocking(webView: WebView, pageUrl: String?) {
        if (!_stats.value.isEnabled || !_stats.value.isCosmeticFilteringEnabled) return
        if (isDomainWhitelisted(pageUrl)) return

        webView.post {
            webView.evaluateJavascript(cachedCosmeticJs, null)
        }
    }
}
