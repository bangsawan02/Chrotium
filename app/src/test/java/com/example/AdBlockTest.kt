package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.AdBlockEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdBlockTest {

    private lateinit var context: Context
    private lateinit var adBlockEngine: AdBlockEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        adBlockEngine = AdBlockEngine(context)
    }

    @Test
    fun testDomainWhitelistingHierarchy() {
        adBlockEngine.toggleDomainWhitelist("example.com")
        
        // Exact match
        assertTrue(adBlockEngine.isDomainWhitelisted("example.com"))
        assertTrue(adBlockEngine.isDomainWhitelisted("https://example.com/page"))
        
        // Subdomain hierarchical match
        assertTrue(adBlockEngine.isDomainWhitelisted("sub.example.com"))
        assertTrue(adBlockEngine.isDomainWhitelisted("https://a.b.example.com/test"))
        
        // Non-whitelisted domain
        assertFalse(adBlockEngine.isDomainWhitelisted("google.com"))
        assertFalse(adBlockEngine.isDomainWhitelisted("notexample.com"))
    }

    @Test
    fun testTrackingUrlParameterCleaning() {
        val dirtyUrl = "https://example.com/page?utm_source=facebook&utm_medium=cpc&fbclid=123456&article_id=99"
        val cleanUrl = adBlockEngine.cleanTrackingParameters(dirtyUrl)
        
        assertFalse(cleanUrl.contains("utm_source"))
        assertFalse(cleanUrl.contains("fbclid"))
        assertTrue(cleanUrl.contains("article_id=99"))
        assertTrue(cleanUrl.startsWith("https://example.com/page"))
    }

    @Test
    fun testScriptletsAndCosmeticCss() {
        assertTrue(adBlockEngine.COSMETIC_AD_BLOCK_CSS.contains("adsbygoogle"))
        assertTrue(adBlockEngine.COSMETIC_AD_BLOCK_CSS.contains("ytp-ad-module"))
        assertTrue(adBlockEngine.ANTI_ADBLOCK_BYPASS_SCRIPT.contains("fuckAdBlock"))
        assertTrue(adBlockEngine.ANTI_FINGERPRINT_SCRIPT.contains("toDataURL"))
    }

    @Test
    fun testSettingsToggles() {
        assertTrue(adBlockEngine.stats.value.isPopupBlockingEnabled)
        adBlockEngine.setPopupBlockingEnabled(false)
        assertFalse(adBlockEngine.stats.value.isPopupBlockingEnabled)

        assertTrue(adBlockEngine.stats.value.isAntiFingerprintingEnabled)
        adBlockEngine.setAntiFingerprintingEnabled(false)
        assertFalse(adBlockEngine.stats.value.isAntiFingerprintingEnabled)
    }
}
