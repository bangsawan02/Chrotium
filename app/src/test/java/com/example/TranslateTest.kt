package com.example

import com.example.engine.TranslateEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateTest {

    @Test
    fun testSupportedLanguages() {
        assertTrue(TranslateEngine.SUPPORTED_LANGUAGES.size >= 25)
        val idLang = TranslateEngine.getLanguageByCode("id")
        assertEquals("Bahasa Indonesia", idLang.name)
        assertEquals("🇮🇩", idLang.flag)

        val jaLang = TranslateEngine.getLanguageByCode("ja")
        assertEquals("日本語", jaLang.name)
        assertEquals("🇯🇵", jaLang.flag)

        val fallback = TranslateEngine.getLanguageByCode("non_existent_code")
        assertEquals("id", fallback.code)
    }

    @Test
    fun testBuildInPageTranslateScript() {
        val script = TranslateEngine.buildInPageTranslateScript("id")
        assertNotNull(script)
        assertTrue(script.contains("googtrans"))
        assertTrue(script.contains("/auto/id"))
        assertTrue(script.contains("google.translate.TranslateElement"))
        assertTrue(script.contains("crotium_google_translate_element"))
    }

    @Test
    fun testBuildRestoreOriginalScript() {
        val script = TranslateEngine.buildRestoreOriginalScript()
        assertNotNull(script)
        assertTrue(script.contains("googtrans=; expires=Thu, 01 Jan 1970"))
        assertTrue(script.contains("goog-te-combo"))
    }

    @Test
    fun testProxyUrls() {
        val googleProxy = TranslateEngine.buildGoogleTranslateProxyUrl("https://example.com/page", "id")
        assertTrue(googleProxy.startsWith("https://translate.google.com/translate"))
        assertTrue(googleProxy.contains("tl=id"))
        assertTrue(googleProxy.contains("example.com"))

        val bingProxy = TranslateEngine.buildBingTranslateProxyUrl("https://example.com/page", "ja")
        assertTrue(bingProxy.startsWith("https://www.translatetheweb.com"))
        assertTrue(bingProxy.contains("to=ja"))
    }
}
