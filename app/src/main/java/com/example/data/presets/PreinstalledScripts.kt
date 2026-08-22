package com.example.data.presets

import com.example.data.model.UserScript

object PreinstalledScripts {
    fun getDefaultScripts(): List<UserScript> = listOf(
        UserScript(
            id = 1,
            name = "Battery Saver CPU Throttler",
            description = "Mengurangi interval timer background JS, memperlambat animasi berat, dan menghemat baterai perangkat.",
            author = "VoltPower Lab",
            version = "1.5",
            matchPatterns = "*://*/*",
            runAt = "document-start",
            code = """
// ==UserScript==
// @name         Battery Saver CPU Throttler
// @namespace    https://darkbrowser.app
// @version      1.6
// @description  Menghemat baterai dengan menghentikan animasi tidak penting (seperti marquee/blink) tanpa merusak web modern.
// @match        *://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(function() {
    'use strict';
    // Hanya menargetkan animasi usang/berat secara visual yang tidak merusak logika state-machine framework modern (React/Angular)
    const style = document.createElement('style');
    style.textContent = `
        marquee, blink {
            animation: none !important;
            display: none !important;
        }
    `;
    (document.head || document.documentElement).appendChild(style);
})();
            """.trimIndent(),
            isEnabled = true,
            isBuiltIn = true
        )
    )
}

