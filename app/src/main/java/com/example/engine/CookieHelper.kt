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
     * Konfigurasi dukungan cookie pihak ketiga (third-party cookies) pada WebView
     */
    fun configureWebViewCookies(webView: WebView, allowThirdParty: Boolean = true) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, allowThirdParty)
    }

    /**
     * Mengambil header cookie mentah untuk URL tertentu jika dibutuhkan engine unduhan/translasi
     */
    fun getRawCookieHeader(url: String): String {
        if (url.isBlank() || url.startsWith("about:")) return ""
        return CookieManager.getInstance().getCookie(url) ?: ""
    }
}
