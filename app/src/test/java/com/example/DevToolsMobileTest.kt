package com.example

import com.example.engine.DevToolsEngine
import com.example.engine.WebConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsMobileTest {

    @Test
    fun testDevToolsUrlGeneration() {
        val url = DevToolsEngine.getDevToolsUrl(
            port = 9222,
            pageId = "PAGE_TEST_123",
            commitHash = DevToolsEngine.DEFAULT_CHROMIUM_HASH
        )
        assertNotNull(url)
        assertTrue(url.contains("chrome-devtools-frontend.appspot.com"))
        assertTrue(url.contains("ws=127.0.0.1:9222/devtools/page/PAGE_TEST_123"))
        assertTrue(url.contains(DevToolsEngine.DEFAULT_CHROMIUM_HASH))
    }

    @Test
    fun testStableChromiumHashesAvailable() {
        assertTrue(DevToolsEngine.STABLE_CHROMIUM_HASHES.isNotEmpty())
        assertTrue(DevToolsEngine.STABLE_CHROMIUM_HASHES.any { it.first == DevToolsEngine.DEFAULT_CHROMIUM_HASH })
    }

    @Test
    fun testElementPickerScripts() {
        assertTrue(DevToolsEngine.ELEMENT_PICKER_SCRIPT.contains("__chrotium_picker_active"))
        assertTrue(DevToolsEngine.DEVTOOLS_FRONTEND_MOBILE_CSS.contains("Mobile Touch Optimization"))
    }

    @Test
    fun testMobileViewportScript() {
        val script = WebConfig.MOBILE_VIEWPORT_SCRIPT
        assertTrue(script.contains("width=device-width"))
        assertTrue(script.contains("initial-scale=1.0"))
        assertTrue(script.contains("mobile: true"))
    }

    @Test
    fun testMobileUserAgent() {
        val ua = WebConfig.getMobileUserAgent()
        assertTrue(ua.contains("Mobile Safari"))
        assertTrue(ua.contains("Android"))
        assertTrue(ua.contains("Chrome/"))
    }
}
