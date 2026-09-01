package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserScript
import com.example.engine.UserScriptEngine
import com.example.engine.WebConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Chrotium Browser", appName)
  }

  @Test
  fun `test userscript metadata parser`() {
    val sampleScript = """
      // ==UserScript==
      // @name         YouTube Ad Skipper & OLED Dark
      // @description  Custom userscript for testing
      // @author       DevUser
      // @version      2.5
      // @match        *://*.youtube.com/*
      // @run-at       document-start
      // ==/UserScript==
      console.log('test');
    """.trimIndent()

    val parsed = UserScript.parseMetadata(sampleScript)
    assertEquals("YouTube Ad Skipper & OLED Dark", parsed.name)
    assertEquals("Custom userscript for testing", parsed.description)
    assertEquals("DevUser", parsed.author)
    assertEquals("2.5", parsed.version)
    assertEquals("document-start", parsed.runAt)
    assertTrue(parsed.matchPatterns.contains("*.youtube.com"))
  }

  @Test
  fun `test userscript url matching logic`() {
    val script = UserScript(
      name = "Wikipedia Enhancer",
      matchPatterns = "*://*.wikipedia.org/*, https://en.wikipedia.org/*",
      code = "console.log('wiki');"
    )

    assertTrue(script.matchesUrl("https://en.wikipedia.org/wiki/Android"))
    assertTrue(script.matchesUrl("https://id.wikipedia.org/wiki/Indonesia"))
    assertFalse(script.matchesUrl("https://google.com"))
    assertFalse(script.matchesUrl("about:blank"))
  }

  @Test
  fun `test webconfig user agents`() {
    val mobileUa = WebConfig.getMobileUserAgent()
    val desktopUa = WebConfig.getDesktopUserAgent()
    assertTrue(mobileUa.contains("Android"))
    assertTrue(desktopUa.contains("Windows"))
  }

  @Test
  fun `test script wrapping in IIFE`() {
    val rawCode = "console.log('hello');"
    val wrapped = UserScriptEngine.buildExecutableScript(
      UserScript(name = "TestScript", matchPatterns = "*", code = rawCode)
    )

    assertTrue(wrapped.contains("function()"))
    assertTrue(wrapped.contains("GM_getValue"))
    assertTrue(wrapped.contains("GM_addStyle"))
  }
}

