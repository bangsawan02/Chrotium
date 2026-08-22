package com.example.engine

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import com.example.data.model.CookieItem
import org.json.JSONArray
import org.json.JSONObject

object CookieHelper {

    /**
     * Mengambil daftar cookie untuk URL aktif
     */
    fun getCookiesForUrl(url: String): List<CookieItem> {
        if (url.isBlank() || url.startsWith("about:")) return emptyList()

        val cookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie(url) ?: return emptyList()

        val domain = try {
            Uri.parse(url).host ?: ""
        } catch (e: Exception) {
            ""
        }

        val result = mutableListOf<CookieItem>()
        val pairs = cookieString.split(";")
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            val splitIndex = trimmed.indexOf('=')
            if (splitIndex != -1) {
                val name = trimmed.substring(0, splitIndex).trim()
                val value = trimmed.substring(splitIndex + 1).trim()
                result.add(
                    CookieItem(
                        name = name,
                        value = value,
                        domain = domain,
                        path = "/",
                        rawString = trimmed
                    )
                )
            } else {
                result.add(
                    CookieItem(
                        name = trimmed,
                        value = "",
                        domain = domain,
                        path = "/",
                        rawString = trimmed
                    )
                )
            }
        }
        return result
    }

    /**
     * Menambahkan atau memperbarui cookie untuk URL
     */
    fun setCookie(url: String, name: String, value: String, domain: String = "", path: String = "/") {
        if (url.isBlank() || name.isBlank()) return
        val cookieManager = CookieManager.getInstance()
        val cookieString = StringBuilder().apply {
            append("$name=$value; ")
            if (domain.isNotBlank()) append("Domain=$domain; ")
            append("Path=$path; ")
            append("SameSite=Lax")
        }.toString()

        cookieManager.setCookie(url, cookieString)
        cookieManager.flush()
    }

    /**
     * Menghapus cookie spesifik berdasarkan nama
     */
    fun deleteCookie(url: String, name: String) {
        if (url.isBlank() || name.isBlank()) return
        val cookieManager = CookieManager.getInstance()
        val expireString = "$name=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0"
        cookieManager.setCookie(url, expireString)
        cookieManager.flush()
    }

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
     * Ekspor cookie dalam format header HTTP (name=value; ...)
     */
    fun exportAsHttpHeader(url: String): String {
        return CookieManager.getInstance().getCookie(url) ?: ""
    }

    /**
     * Ekspor cookie dalam format JSON terstruktur
     */
    fun exportAsJson(url: String): String {
        val cookies = getCookiesForUrl(url)
        val jsonArray = JSONArray()
        for (c in cookies) {
            val obj = JSONObject()
            obj.put("name", c.name)
            obj.put("value", c.value)
            obj.put("domain", c.domain)
            obj.put("path", c.path)
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }
}
