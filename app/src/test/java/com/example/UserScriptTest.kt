package com.example

import com.example.data.model.UserScript
import com.example.engine.UserScriptEngine
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
            // @include      https://sub.domain.org/*
            // @exclude      https://*.example.com/login*
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
        assertTrue(meta.matchPatterns.contains("*://*.example.com/*"))
        assertTrue(meta.matchPatterns.contains("https://sub.domain.org/*"))
        assertTrue(meta.matchPatterns.contains("!https://*.example.com/login*"))
    }

    @Test
    fun testUrlMatchingSubdomainsAndRoot() {
        val script = UserScript(
            id = 1L,
            name = "Test Matcher",
            matchPatterns = "*://*.google.com/*, https://github.com/*",
            code = "// code",
            isEnabled = true
        )

        // Root domain matching
        assertTrue(script.matchesUrl("https://google.com/"))
        assertTrue(script.matchesUrl("https://google.com/search?q=test"))
        // Subdomain matching
        assertTrue(script.matchesUrl("https://www.google.com/search?q=test"))
        assertTrue(script.matchesUrl("https://mail.google.com/mail/u/0"))
        assertTrue(script.matchesUrl("http://google.com/"))
        // Other pattern
        assertTrue(script.matchesUrl("https://github.com/repository"))
        assertFalse(script.matchesUrl("https://wikipedia.org"))
        assertFalse(script.matchesUrl("about:blank"))
    }

    @Test
    fun testUrlExclusionRules() {
        val script = UserScript(
            id = 2L,
            name = "Test Exclusion",
            matchPatterns = "*://*/*, !*://*.google.com/*, !https://facebook.com/*",
            code = "// code",
            isEnabled = true
        )

        assertTrue(script.matchesUrl("https://github.com/"))
        assertTrue(script.matchesUrl("https://wikipedia.org/wiki/Kotlin"))
        // Excluded
        assertFalse(script.matchesUrl("https://google.com/"))
        assertFalse(script.matchesUrl("https://www.google.com/search"))
        assertFalse(script.matchesUrl("https://facebook.com/home"))
    }

    @Test
    fun testRegexMatching() {
        val script = UserScript(
            id = 3L,
            name = "Regex Matcher",
            matchPatterns = "/^https?:\\/\\/.*\\.youtube\\.com\\/watch\\?v=.+$/",
            code = "// code",
            isEnabled = true
        )

        assertTrue(script.matchesUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(script.matchesUrl("https://m.youtube.com/watch?v=12345"))
        assertFalse(script.matchesUrl("https://www.youtube.com/feed/subscriptions"))
        assertFalse(script.matchesUrl("https://vimeo.com/12345"))
    }

    @Test
    fun testBuildExecutableScript() {
        val script = UserScript(
            id = 4L,
            name = "Test Script GM",
            description = "A unit test script",
            author = "Dev",
            version = "1.2.3",
            matchPatterns = "*://*/*",
            runAt = "document-idle",
            code = """
                console.log('GM_info check', GM_info.script.name);
                GM_setValue('theme', 'dark');
            """.trimIndent(),
            isEnabled = true
        )

        val js = UserScriptEngine.buildExecutableScript(script)
        assertTrue(js.contains("const unsafeWindow ="))
        assertTrue(js.contains("const GM_info ="))
        assertTrue(js.contains("Test Script GM"))
        assertTrue(js.contains("function GM_addStyle"))
        assertTrue(js.contains("function GM_setValue"))
        assertTrue(js.contains("function GM_getValue"))
        assertTrue(js.contains("function GM_xmlhttpRequest"))
        assertTrue(js.contains("const GM ="))
    }
}

