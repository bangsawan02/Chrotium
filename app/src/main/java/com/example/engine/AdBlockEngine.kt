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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import android.util.Log

data class AdBlockStats(
    val isEnabled: Boolean = true,
    val isCosmeticFilteringEnabled: Boolean = true,
    val isTrackerBlockingEnabled: Boolean = true,
    val isPopupBlockingEnabled: Boolean = true,
    val isAntiFingerprintingEnabled: Boolean = true,
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
            isPopupBlockingEnabled = prefs.getBoolean("popups_enabled", true),
            isAntiFingerprintingEnabled = prefs.getBoolean("fingerprinting_enabled", true),
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
        "shorte.st",
        "adf.ly",
        "ouo.io",
        "linkvertise.com",
        "tinylink.net",
        "bc.vc",

        // Web Push Notification & Popunder Spam
        "pushnotifs.com",
        "push-notification.live",
        "notification-service.com",
        "pushpro.net",
        "propu.sh",
        "izooto.com",
        "webpushtracking.com",

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
        "loggly.com",
        "bugsnag.com",
        "sentry.io",
        "newrelic.com",

        // Cryptominers & malicious scripts
        "coinhive.com",
        "crypto-loot.com",
        "webminepool.com",
        "jsecoin.com",
        "minr.pw",
        "coin-have.com",
        "cryptoloot.pro"
    )

    // Fast keyword signatures for trackers, analytics, telemetry, and ad endpoints
    private val blockedUrlKeywords = arrayOf(
        "/ads/", "/ads.", "/ad.", "/ad_", "/ad/",
        "/pagead/", "/popunder", "/banner_ad",
        "/telemetry/", "/tracker/", "/pixel/",
        "fbevents.js", "gtag/js?id=", "analytics.js",
        "ad_service", "advertising", "advertisement",
        "/collect?v=", "google-analytics", "doubleclick"
    )

    private var cachedPageUrl: String? = null
    private var cachedPageHost: String? = null

    private class TrieNode {
        val children = HashMap<String, TrieNode>(4)
        var isEndOfDomain = false
    }

    private val domainTrieRoot = TrieNode()

    init {
        // Build Trie structure for extremely fast suffix domain matching
        val combinedSuffixes = blockedDomainSuffixes.toMutableSet()

        // Recover user data: immediately delete legacy large SharedPreferences string set
        if (prefs.contains("cloud_blocked_domains")) {
            prefs.edit().remove("cloud_blocked_domains").apply()
        }

        // Load cloud domains from local file instead of SharedPreferences to save RAM/Disk
        val cloudFile = java.io.File(context.filesDir, "adblock_cloud.txt")
        if (cloudFile.exists()) {
            try {
                cloudFile.useLines { lines ->
                    combinedSuffixes.addAll(lines.filter { it.isNotBlank() })
                }
            } catch (e: Exception) {
                // Ignore file read error
            }
        }

        combinedSuffixes.forEach { domain ->
            insertDomainToTrie(domain)
        }
    }

    private fun insertDomainToTrie(domain: String) {
        val clean = domain.trim().lowercase()
        if (clean.isBlank()) return
        var current = domainTrieRoot
        var end = clean.length
        while (end > 0) {
            val start = clean.lastIndexOf('.', end - 1)
            val segment = if (start == -1) clean.substring(0, end) else clean.substring(start + 1, end)
            if (segment.isNotEmpty()) {
                var next = current.children[segment]
                if (next == null) {
                    next = TrieNode()
                    current.children[segment] = next
                }
                current = next
            }
            if (start == -1) break
            end = start
        }
        current.isEndOfDomain = true
    }

    /**
     * Zero-allocation reverse-domain segment traversal on the Trie
     */
    private fun isHostBlocked(host: String): Boolean {
        if (host.isBlank()) return false
        var current = domainTrieRoot
        var end = host.length
        while (end > 0) {
            val start = host.lastIndexOf('.', end - 1)
            val segment = if (start == -1) host.substring(0, end) else host.substring(start + 1, end)
            if (segment.isNotEmpty()) {
                current = current.children[segment] ?: return false
                if (current.isEndOfDomain) return true
            }
            if (start == -1) break
            end = start
        }
        return false
    }

    /**
     * Memeriksa apakah request jaringan harus diblokir
     */
    fun shouldBlockRequest(request: WebResourceRequest, pageUrl: String?): Boolean {
        val currentStats = _stats.value
        if (!currentStats.isEnabled) return false

        val uri = request.url ?: return false
        val host = uri.host?.lowercase() ?: return false
        val fullUrl = uri.toString()

        // Cache parsing of pageUrl to avoid calling Uri.parse hundreds of times per second
        if (pageUrl != null && pageUrl != cachedPageUrl) {
            cachedPageUrl = pageUrl
            cachedPageHost = try {
                if (pageUrl.startsWith("http://") || pageUrl.startsWith("https://")) {
                    Uri.parse(pageUrl).host?.lowercase()
                } else {
                    pageUrl.lowercase()
                }
            } catch (e: Exception) {
                pageUrl.lowercase()
            }
        }

        // Periksa apakah domain induk atau domain aktif ada di daftar pengecualian (whitelist) secara O(1) hash lookup
        if (isDomainWhitelisted(cachedPageHost) || isDomainWhitelisted(host)) {
            return false
        }

        // 1. Host-based Trie blocklist check (O(k) where k is domain depth)
        if (isHostBlocked(host)) {
            recordBlockedRequest(fullUrl)
            return true
        }

        // 2. Path & Tracker keyword matching check (fast non-backtracking scan)
        if (currentStats.isTrackerBlockingEnabled) {
            if (isUrlPatternBlocked(fullUrl)) {
                recordBlockedRequest(fullUrl)
                return true
            }
        }

        return false
    }

    /**
     * Fast substring / token scan avoiding heavy Regex backtracking
     */
    private fun isUrlPatternBlocked(url: String): Boolean {
        val lowerUrl = url.lowercase()
        for (i in blockedUrlKeywords.indices) {
            if (lowerUrl.contains(blockedUrlKeywords[i])) {
                return true
            }
        }
        return false
    }

    /**
     * Fast O(k) hierarchical domain whitelist check using HashSet indexing
     */
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
        if (whitelisted.isEmpty() || host.isBlank()) return false

        // Exact match check O(1)
        if (whitelisted.contains(host)) return true

        // Check parent domains (e.g. sub.example.com -> example.com)
        var dotIndex = host.indexOf('.')
        while (dotIndex != -1 && dotIndex < host.length - 1) {
            val parentDomain = host.substring(dotIndex + 1)
            if (whitelisted.contains(parentDomain)) return true
            dotIndex = host.indexOf('.', dotIndex + 1)
        }

        return false
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

    fun setPopupBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("popups_enabled", enabled).apply()
        _stats.value = _stats.value.copy(isPopupBlockingEnabled = enabled)
    }

    fun setAntiFingerprintingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("fingerprinting_enabled", enabled).apply()
        _stats.value = _stats.value.copy(isAntiFingerprintingEnabled = enabled)
    }

    /**
     * Clear URL tracking parameters (utm_*, fbclid, gclid, etc.) to prevent cross-site tracking
     */
    fun cleanTrackingParameters(url: String): String {
        if (!_stats.value.isTrackerBlockingEnabled || url.isBlank()) return url
        return try {
            val uri = Uri.parse(url)
            if (uri.query.isNullOrBlank()) return url

            val trackingParams = setOf(
                "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
                "fbclid", "gclid", "msclkid", "ttclid", "twclid", "igshid", "_hsenc", "yclid", "mc_eid"
            )

            val builder = uri.buildUpon().clearQuery()
            var hasChanges = false
            for (param in uri.queryParameterNames) {
                if (trackingParams.contains(param.lowercase())) {
                    hasChanges = true
                } else {
                    for (valItem in uri.getQueryParameters(param)) {
                        builder.appendQueryParameter(param, valItem)
                    }
                }
            }
            if (hasChanges) builder.build().toString() else url
        } catch (e: Exception) {
            url
        }
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
        [class*="ad-placement"],
        [class*="ad-box"],
        [class*="ad-space"],
        [class*="ad-zone"],
        [id*="banner-ad"],
        [id*="taboola-"],
        [id*="outbrain_"],
        [id*="adblock-warning"],
        [class*="adblock-modal"],
        [class*="anti-adblock"],
        [id*="anti-adblock"],
        .trc_related_container,
        .mgbox,
        .ad-placement,
        .advertisement,
        .ad_top,
        .ad_bottom,
        .ad_sidebar,
        .sticky-ad,
        .bottom-ad,
        .floating-ad,
        .ytp-ad-module,
        .ytp-ad-overlay-container,
        .video-ads,
        iframe[src*="doubleclick.net"],
        iframe[src*="googlesyndication.com"],
        iframe[src*="adservice.google.com"],
        iframe[src*="adnxs.com"],
        iframe[src*="taboola.com"],
        iframe[src*="popads.net"],
        iframe[src*="propellerads.com"] {
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
     * Pre-computed JavaScript injection string for cosmetic filtering & dynamic DOM MutationObserver
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

                if (!window.__crotium_adblock_observer) {
                    var removeDynamicAds = function() {
                        var selectors = [
                            '.ytp-ad-module', '.ytp-ad-overlay-container',
                            'a[href*="doubleclick.net"]', 'a[href*="popads.net"]',
                            'a[href*="propellerads.com"]', 'div[id^="google_ads_"]'
                        ];
                        for (var i = 0; i < selectors.length; i++) {
                            var els = document.querySelectorAll(selectors[i]);
                            for (var j = 0; j < els.length; j++) {
                                els[j].remove();
                            }
                        }
                    };
                    var observer = new MutationObserver(function() {
                        removeDynamicAds();
                    });
                    if (document.body || document.documentElement) {
                        observer.observe(document.body || document.documentElement, { childList: true, subtree: true });
                    }
                    window.__crotium_adblock_observer = observer;
                }
            } catch(e) {}
        })();
        """.trimIndent()
    }

    /**
     * Anti-AdBlock bypass scriptlet to defeat FuckAdBlock/BlockAdBlock detectors
     */
    val ANTI_ADBLOCK_BYPASS_SCRIPT: String = """
        (function() {
            try {
                window.fuckAdBlock = undefined;
                window.blockAdBlock = undefined;
                window.canRunAds = true;
                window.isAdBlockActive = false;
                window.google_ad_status = 1;

                if (!window.ga) {
                    window.ga = function() {};
                    window.ga.q = [];
                }
                if (!window.gtag) {
                    window.gtag = function() {};
                }
            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * Anti-Fingerprinting canvas protection scriptlet
     */
    val ANTI_FINGERPRINT_SCRIPT: String = """
        (function() {
            try {
                if (HTMLCanvasElement.prototype.toDataURL) {
                    var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
                    HTMLCanvasElement.prototype.toDataURL = function() {
                        var ctx = this.getContext('2d');
                        if (ctx) {
                            try {
                                var imgData = ctx.getImageData(0, 0, Math.min(this.width, 10), Math.min(this.height, 10));
                                if (imgData && imgData.data && imgData.data.length > 0) {
                                    imgData.data[0] = (imgData.data[0] + 1) % 255;
                                }
                            } catch(e) {}
                        }
                        return origToDataURL.apply(this, arguments);
                    };
                }
            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * Injeksi CSS Cosmetic Ad-Hiding & Scriptlets ke WebView
     */
    fun injectCosmeticAdBlocking(webView: WebView, pageUrl: String?) {
        val currentStats = _stats.value
        if (!currentStats.isEnabled) return
        if (isDomainWhitelisted(pageUrl)) return

        webView.post {
            if (currentStats.isCosmeticFilteringEnabled) {
                webView.evaluateJavascript(cachedCosmeticJs, null)
            }
            webView.evaluateJavascript(ANTI_ADBLOCK_BYPASS_SCRIPT, null)
            if (currentStats.isAntiFingerprintingEnabled) {
                webView.evaluateJavascript(ANTI_FINGERPRINT_SCRIPT, null)
            }
        }
    }

    /**
     * Mengambil daftar blokir terbaru (AdBlock List) dari Cloud.
     * Mengunduh daftar dari sumber terbuka untuk memperbarui pemblokir iklan secara dinamis.
     */
    suspend fun updateBlocklistFromCloud() = withContext(Dispatchers.IO) {
        try {
            // URL Sumber Blocklist contoh (StevenBlack list untuk ad/malware)
            // Menggunakan URL ringan dengan format text per-baris (1 domain per baris)
            val url = URL("https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-gambling-porn/hosts")
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val lines = connection.getInputStream().bufferedReader().useLines { it.toList() }
            val newDomains = mutableSetOf<String>()
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#") || trimmed.isEmpty()) continue
                // Format file hosts biasanya: "0.0.0.0 domain.com"
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val domain = parts[1]
                    if (domain != "localhost" && domain != "local") {
                        newDomains.add(domain)
                    }
                }
            }
            
            if (newDomains.isNotEmpty()) {
                // Tambahkan domain baru ke dalam Trie
                newDomains.forEach { domain ->
                    insertDomainToTrie(domain)
                }

                // Simpan di file teks biasa agar tidak membebani SharedPreferences
                val cloudFile = java.io.File(context.filesDir, "adblock_cloud.txt")
                cloudFile.writeText(newDomains.joinToString("\n"))
            }
        } catch (e: Exception) {
            Log.e("AdBlockEngine", "Gagal memperbarui AdBlock dari Cloud", e)
        }
    }
}
