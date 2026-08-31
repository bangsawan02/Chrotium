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
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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

    // Bounded Concurrent Cache untuk evaluasi host (Lookup O(1) cepat dengan 0 alokasi)
    private val hostBlockCache = ConcurrentHashMap<String, Boolean>(512)
    private val maxCacheSize = 2048

    // Thread-safe HashSet berisi seluruh daftar domain yang diblokir
    private val blockedDomains: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())

    // Hardcoded static high-priority blocklist (iklan, pelacak, analitik utama)
    private val staticBlockedDomains = hashSetOf(
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

    @Volatile
    private var cachedPageUrl: String? = null
    @Volatile
    private var cachedPageHost: String? = null

    init {
        // Gabungkan seluruh daftar domain
        val compiledSet = HashSet<String>(12000)
        compiledSet.addAll(staticBlockedDomains)

        // Hapus entri kuki/preferences lama jika ada
        if (prefs.contains("cloud_blocked_domains")) {
            prefs.edit().remove("cloud_blocked_domains").apply()
        }

        // Muat database domain dari cloud lokal jika ada
        val cloudFile = File(context.filesDir, "adblock_cloud.txt")
        if (cloudFile.exists()) {
            try {
                cloudFile.useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim().lowercase()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            compiledSet.add(trimmed)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AdBlockEngine", "Gagal memuat adblock_cloud.txt", e)
            }
        }

        synchronized(blockedDomains) {
            blockedDomains.addAll(compiledSet)
        }
        Log.d("AdBlockEngine", "AdBlock diinisialisasi dengan ${blockedDomains.size} domain terblokir.")
    }

    /**
     * Memvalidasi apakah sebuah host terblokir dengan pencarian bertingkat O(1).
     * Contoh: ads.doubleclick.net -> doubleclick.net -> net
     */
    private fun isHostBlocked(host: String): Boolean {
        if (host.isBlank()) return false
        
        // 1. Ambil dari cache keputusan (menghindari manipulasi String dan GC overhead)
        val cached = hostBlockCache[host]
        if (cached != null) {
            return cached
        }

        // 2. Evaluasi hierarkis domain
        var isBlocked = blockedDomains.contains(host)
        if (!isBlocked) {
            var dotIndex = host.indexOf('.')
            while (dotIndex != -1 && dotIndex < host.length - 1) {
                val parentDomain = host.substring(dotIndex + 1)
                if (blockedDomains.contains(parentDomain)) {
                    isBlocked = true
                    break
                }
                dotIndex = host.indexOf('.', dotIndex + 1)
            }
        }

        // 3. Simpan hasil keputusan ke cache (Pruning jika penuh untuk mencegah OOM)
        if (hostBlockCache.size >= maxCacheSize) {
            hostBlockCache.clear()
        }
        hostBlockCache[host] = isBlocked
        return isBlocked
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

        // Cache halaman pemanggil untuk meminimalkan parsing URI berulang kali
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

        // Bypass jika domain induk atau domain aktif dikecualikan (whitelist)
        if (isDomainWhitelisted(cachedPageHost) || isDomainWhitelisted(host)) {
            return false
        }

        // 1. Pencarian hierarki berbasis HashSet & Cache (Sangat cepat & hemat RAM)
        if (isHostBlocked(host)) {
            if (host != "localhost" && !host.endsWith(".local")) {
                recordBlockedRequest(fullUrl)
            }
            return true
        }

        // 2. Pemindaian kata kunci URL (Case-insensitive langsung tanpa alokasi lowercase baru)
        if (currentStats.isTrackerBlockingEnabled) {
            if (isUrlPatternBlocked(fullUrl)) {
                recordBlockedRequest(fullUrl)
                return true
            }
        }

        return false
    }

    /**
     * Case-insensitive scanning yang mengeliminasi pembentukan objek string baru
     */
    private fun isUrlPatternBlocked(url: String): Boolean {
        for (i in blockedUrlKeywords.indices) {
            if (url.contains(blockedUrlKeywords[i], ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Pemeriksaan whitelist O(1) dengan pencarian berjenjang
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

        if (whitelisted.contains(host)) return true

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
        
        // Hapus cache pemblokiran host karena status whitelist berubah
        hostBlockCache.clear()
    }

    private val totalBlockedAtomic = AtomicLong(prefs.getLong("total_blocked_count", 0L))
    private val savedDataAtomic = AtomicLong(prefs.getLong("saved_data_kb", 0L))

    private fun recordBlockedRequest(url: String) {
        val currentBlocked = totalBlockedAtomic.incrementAndGet()
        // Estimasi hemat kuota ~45 KB per iklan
        val currentSavedKb = savedDataAtomic.addAndGet(45L)

        _stats.value = _stats.value.copy(
            totalBlockedCount = currentBlocked,
            savedDataKbEstimate = currentSavedKb
        )

        if (currentBlocked % 10L == 0L) {
            prefs.edit()
                .putLong("total_blocked_count", currentBlocked)
                .putLong("saved_data_kb", currentSavedKb)
                .apply()
        }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("adblock_enabled", enabled).apply()
        _stats.value = _stats.value.copy(isEnabled = enabled)
        hostBlockCache.clear()
    }

    fun setCosmeticFilteringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("cosmetic_enabled", enabled).apply()
        _stats.value = _stats.value.copy(isCosmeticFilteringEnabled = enabled)
    }

    fun setTrackerBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("trackers_enabled", enabled).apply()
        _stats.value = _stats.value.copy(isTrackerBlockingEnabled = enabled)
        hostBlockCache.clear()
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
     * Pembersihan parameter URL pelacak (utm_*, fbclid, dsb) secara instan
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
        totalBlockedAtomic.set(0L)
        savedDataAtomic.set(0L)
        _stats.value = _stats.value.copy(totalBlockedCount = 0L, savedDataKbEstimate = 0L)
    }

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

    suspend fun updateBlocklistFromCloud() = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-gambling-porn/hosts")
            val connection = url.openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val lines = connection.getInputStream().bufferedReader().useLines { it.toList() }
            val newDomains = mutableSetOf<String>()

            var count = 0
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#") || trimmed.isEmpty()) continue

                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val domain = parts[1].lowercase()
                    if (domain != "localhost" && domain != "local" && domain.contains('.')) {
                        newDomains.add(domain)
                        count++
                    }
                }
                if (count >= 10000) break
            }

            if (newDomains.isNotEmpty()) {
                val compiledSet = HashSet<String>(blockedDomains.size + newDomains.size)
                compiledSet.addAll(staticBlockedDomains)
                compiledSet.addAll(newDomains)
                
                synchronized(blockedDomains) {
                    blockedDomains.clear()
                    blockedDomains.addAll(compiledSet)
                }

                // Bersihkan cache pencarian karena list berubah
                hostBlockCache.clear()

                val cloudFile = File(context.filesDir, "adblock_cloud.txt")
                cloudFile.writeText(newDomains.joinToString("\n"))
                Log.d("AdBlockEngine", "Daftar blokir cloud diperbarui dengan ${newDomains.size} domain baru.")
            }
        } catch (e: Exception) {
            Log.e("AdBlockEngine", "Gagal memperbarui AdBlock dari Cloud", e)
        }
    }
}
