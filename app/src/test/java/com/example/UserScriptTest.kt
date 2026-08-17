package com.example

import com.example.data.model.UserScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptTest {

    @Test
    fun testParseMetadata() {
        val sampleCode = """
            // ==UserScript==
            // @name         AMOLED Dark Test
            // @namespace    https://crotium.app
            // @version      2.5
            // @description  Mengubah halaman menjadi gelap sejati
            // @author       Tester
            // @match        *://*.example.com/*
            // @run-at       document-start
            // @grant        GM_addStyle
            // ==/UserScript==
            (function() { console.log('active'); })();
        """.trimIndent()

        val meta = UserScript.parseMetadata(sampleCode)
        assertEquals("AMOLED Dark Test", meta.name)
        assertEquals("2.5", meta.version)
        assertEquals("Mengubah halaman menjadi gelap sejati", meta.description)
        assertEquals("Tester", meta.author)
        assertEquals("document-start", meta.runAt)
        assertEquals("*://*.example.com/*", meta.matchPatterns)
    }

    @Test
    fun testUrlMatching() {
        val script = UserScript(
            id = 1L,
            name = "Test Matcher",
            matchPatterns = "*://*.google.com/*, https://github.com/*",
            code = "// code",
            isEnabled = true
        )

        assertTrue(script.matchesUrl("https://www.google.com/search?q=test"))
        assertTrue(script.matchesUrl("https://github.com/repository"))
        assertFalse(script.matchesUrl("https://wikipedia.org"))
        assertFalse(script.matchesUrl("about:blank"))
    }
}
