package com.example.engine

import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TargetLanguage(
    val code: String,
    val name: String,
    val nativeName: String,
    val flag: String
)

enum class TranslateMode(val title: String, val description: String) {
    IN_PAGE("Terjemahan Langsung (In-Situ)", "Menerjemahkan halaman web tanpa me-reload atau mengubah URL"),
    WEB_PROXY_GOOGLE("Google Translate Web", "Membuka halaman melalui server proxy resmi Google Translate"),
    WEB_PROXY_BING("Bing Translator Web", "Membuka halaman melalui server proxy Microsoft Bing"),
    TEXT_SNIPPET("Penerjemah Teks Cepat", "Menerjemahkan paragraf atau kalimat pilihan secara instan")
}

data class TranslationBarState(
    val isVisible: Boolean = false,
    val isTranslating: Boolean = false,
    val isTranslated: Boolean = false,
    val originalLang: String = "Otomatis",
    val targetLanguage: TargetLanguage = TranslateEngine.SUPPORTED_LANGUAGES[0],
    val currentMode: TranslateMode = TranslateMode.IN_PAGE
)

object TranslateEngine {

    val SUPPORTED_LANGUAGES = listOf(
        TargetLanguage("id", "Bahasa Indonesia", "Indonesia", "🇮🇩"),
        TargetLanguage("en", "English", "Inggris", "🇺🇸"),
        TargetLanguage("ja", "日本語", "Jepang", "🇯🇵"),
        TargetLanguage("zh-CN", "简体中文", "Mandarin (Sederhana)", "🇨🇳"),
        TargetLanguage("zh-TW", "繁體中文", "Mandarin (Tradisional)", "🇹🇼"),
        TargetLanguage("ar", "العربية", "Arab", "🇸🇦"),
        TargetLanguage("ko", "한국어", "Korea", "🇰🇷"),
        TargetLanguage("de", "Deutsch", "Jerman", "🇩🇪"),
        TargetLanguage("fr", "Français", "Prancis", "🇫🇷"),
        TargetLanguage("es", "Español", "Spanyol", "🇪🇸"),
        TargetLanguage("ru", "Русский", "Rusia", "🇷🇺"),
        TargetLanguage("pt", "Português", "Portugis", "🇧🇷"),
        TargetLanguage("it", "Italiano", "Italia", "🇮🇹"),
        TargetLanguage("nl", "Nederlands", "Belanda", "🇳🇱"),
        TargetLanguage("tr", "Türkçe", "Turki", "🇹🇷"),
        TargetLanguage("vi", "Tiếng Việt", "Vietnam", "🇻🇳"),
        TargetLanguage("th", "ไทย", "Thailand", "🇹🇭"),
        TargetLanguage("ms", "Bahasa Melayu", "Malaysia", "🇲🇾"),
        TargetLanguage("tl", "Tagalog", "Filipina", "🇵🇭"),
        TargetLanguage("hi", "हिन्दी", "Hindi", "🇮🇳"),
        TargetLanguage("pl", "Polski", "Polandia", "🇵🇱"),
        TargetLanguage("uk", "Українська", "Ukraina", "🇺🇦"),
        TargetLanguage("sv", "Svenska", "Swedia", "🇸🇪"),
        TargetLanguage("el", "Ελληνικά", "Yunani", "🇬🇷"),
        TargetLanguage("cs", "Čeština", "Ceko", "🇨🇿"),
        TargetLanguage("ro", "Română", "Rumania", "🇷🇴"),
        TargetLanguage("hu", "Magyar", "Hungaria", "🇭🇺"),
        TargetLanguage("da", "Dansk", "Denmark", "🇩🇰"),
        TargetLanguage("fi", "Suomi", "Finlandia", "🇫🇮"),
        TargetLanguage("no", "Norsk", "Norwegia", "🇳🇴"),
        TargetLanguage("he", "עברית", "Ibrani", "🇮🇱"),
        TargetLanguage("fa", "فارسی", "Persia", "🇮🇷")
    )

    fun getLanguageByCode(code: String): TargetLanguage {
        return SUPPORTED_LANGUAGES.find { it.code.equals(code, ignoreCase = true) }
            ?: SUPPORTED_LANGUAGES[0]
    }

    /**
     * Builds in-page JavaScript code that seamlessly translates the live DOM using Google Translate Widget API.
     */
    fun buildInPageTranslateScript(targetCode: String): String {
        return """
            (function() {
                try {
                    // Set translation cookie for google translate
                    var host = window.location.hostname;
                    var cookieVal = '/auto/$targetCode';
                    document.cookie = 'googtrans=' + cookieVal + '; path=/; domain=' + host;
                    document.cookie = 'googtrans=' + cookieVal + '; path=/';

                    // Inject CSS to hide annoying default top translation banners
                    var styleId = 'crotium-translate-style';
                    var existingStyle = document.getElementById(styleId);
                    if (!existingStyle) {
                        var st = document.createElement('style');
                        st.id = styleId;
                        st.textContent = `
                            .goog-te-banner-frame { display: none !important; }
                            .goog-te-gadget { display: none !important; }
                            body { top: 0px !important; position: static !important; }
                            #goog-gt-tt { display: none !important; }
                            .goog-text-highlight { background: none !important; box-shadow: none !important; }
                        `;
                        (document.head || document.documentElement).appendChild(st);
                    }

                    function triggerTranslate() {
                        var select = document.querySelector('.goog-te-combo');
                        if (select) {
                            select.value = '$targetCode';
                            select.dispatchEvent(new Event('change'));
                            return true;
                        }
                        return false;
                    }

                    if (window.google && window.google.translate && window.google.translate.TranslateElement) {
                        if (!triggerTranslate()) {
                            // Element exists but not instantiated
                            new window.google.translate.TranslateElement({
                                pageLanguage: 'auto',
                                layout: window.google.translate.TranslateElement.InlineLayout.SIMPLE,
                                autoDisplay: false
                            }, 'crotium_google_translate_element');
                            setTimeout(triggerTranslate, 300);
                        }
                    } else {
                        // Create hidden target element
                        var el = document.getElementById('crotium_google_translate_element');
                        if (!el) {
                            el = document.createElement('div');
                            el.id = 'crotium_google_translate_element';
                            el.style.display = 'none';
                            document.body.appendChild(el);
                        }

                        window.googleTranslateElementInit = function() {
                            try {
                                new window.google.translate.TranslateElement({
                                    pageLanguage: 'auto',
                                    layout: window.google.translate.TranslateElement.InlineLayout.SIMPLE,
                                    autoDisplay: false
                                }, 'crotium_google_translate_element');
                                setTimeout(triggerTranslate, 400);
                            } catch (e) {
                                console.warn('Translate init error', e);
                            }
                        };

                        var script = document.getElementById('crotium-translate-script');
                        if (!script) {
                            script = document.createElement('script');
                            script.id = 'crotium-translate-script';
                            script.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
                            document.head.appendChild(script);
                        }
                    }
                } catch (e) {
                    console.error('In-page translation error:', e);
                }
            })();
        """.trimIndent()
    }

    /**
     * Builds JavaScript to restore the original un-translated page text.
     */
    fun buildRestoreOriginalScript(): String {
        return """
            (function() {
                try {
                    var host = window.location.hostname;
                    document.cookie = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; domain=' + host;
                    document.cookie = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/';
                    
                    var select = document.querySelector('.goog-te-combo');
                    if (select) {
                        select.value = '';
                        select.dispatchEvent(new Event('change'));
                    }
                    
                    // Trigger iframe close if present
                    var iframe = document.querySelector('iframe.goog-te-banner-frame');
                    if (iframe && iframe.contentWindow) {
                        var closeBtn = iframe.contentWindow.document.querySelector('.goog-close-link');
                        if (closeBtn) closeBtn.click();
                    }
                } catch (e) {
                    console.error('Restore translation error:', e);
                }
            })();
        """.trimIndent()
    }

    /**
     * Builds Google Translate Proxy Web URL.
     */
    fun buildGoogleTranslateProxyUrl(url: String, targetLangCode: String): String {
        val encoded = try {
            URLEncoder.encode(url, "UTF-8")
        } catch (e: Exception) {
            url
        }
        return "https://translate.google.com/translate?sl=auto&tl=$targetLangCode&u=$encoded"
    }

    /**
     * Builds Bing Translator Proxy Web URL.
     */
    fun buildBingTranslateProxyUrl(url: String, targetLangCode: String): String {
        val encoded = try {
            URLEncoder.encode(url, "UTF-8")
        } catch (e: Exception) {
            url
        }
        return "https://www.translatetheweb.com/?to=$targetLangCode&a=$encoded"
    }

    /**
     * Fast single-text translation using Google's public translation endpoint without dependencies.
     */
    suspend fun translateText(
        text: String,
        targetCode: String,
        sourceCode: String = "auto"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success("")
        try {
            val encodedQuery = URLEncoder.encode(text, "UTF-8")
            val endpoint = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceCode&tl=$targetCode&dt=t&q=$encodedQuery"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0)")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonArray = JSONArray(response)
                val sentencesArray = jsonArray.optJSONArray(0)
                val sb = java.lang.StringBuilder()
                if (sentencesArray != null) {
                    for (i in 0 until sentencesArray.length()) {
                        val sentence = sentencesArray.optJSONArray(i)
                        if (sentence != null) {
                            sb.append(sentence.optString(0, ""))
                        }
                    }
                }
                val translatedText = sb.toString()
                if (translatedText.isNotEmpty()) {
                    Result.success(translatedText)
                } else {
                    Result.success(text)
                }
            } else {
                Result.failure(Exception("HTTP Error: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
