package com.example.engine

import android.webkit.CookieManager
import android.webkit.WebView

object CookieHelper {

    /**
     * Menghapus seluruh cookie peramban
     */
    fun clearAllCookies(onFinished: () -> Unit = {}) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies {
            cookieManager.flush()
            onFinished()
        }
    }

    /**
     * Konfigurasi dukungan penuh untuk cookie dan cookie pihak ketiga (third-party cookies) pada WebView
     */
    fun configureWebViewCookies(webView: WebView, allowThirdParty: Boolean = true) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, allowThirdParty)
        
        try {
            @Suppress("DEPRECATION")
            CookieManager.setAcceptFileSchemeCookies(true)
        } catch (_: Throwable) {}
    }

    /**
     * Inisialisasi pengaturan cookie global pada tingkat aplikasi
     */
    fun initializeGlobalCookiePolicy() {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            @Suppress("DEPRECATION")
            CookieManager.setAcceptFileSchemeCookies(true)
            cookieManager.flush()
        } catch (_: Throwable) {}
    }

    /**
     * Mengambil header cookie mentah untuk URL tertentu jika dibutuhkan engine unduhan/translasi
     */
    fun getRawCookieHeader(url: String): String {
        if (url.isBlank() || url.startsWith("about:")) return ""
        return CookieManager.getInstance().getCookie(url) ?: ""
    }
}
