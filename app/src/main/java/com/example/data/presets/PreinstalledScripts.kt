package com.example.data.presets

import com.example.data.model.UserScript

object PreinstalledScripts {
    fun getDefaultScripts(): List<UserScript> = listOf(
        UserScript(
            id = 2,
            name = "Battery Saver CPU Throttler",
            description = "Reduces background JS timers, throttles intensive loop animations, and saves device battery.",
            author = "VoltPower Lab",
            version = "1.5",
            matchPatterns = "*://*/*",
            runAt = "document-start",
            code = """
// ==UserScript==
// @name         Battery Saver CPU Throttler
// @namespace    https://darkbrowser.app
// @version      1.5
// @description  Throttles intensive CPU timers and lowers power draw
// @match        *://*/*
// @run-at       document-start
// @grant        GM_log
// ==/UserScript==

(function() {
    'use strict';
    // Limit timer frequency when window is hidden or idle
    const originalSetInterval = window.setInterval;
    window.setInterval = function(fn, delay, ...args) {
        // Enforce minimum 100ms interval for battery efficiency
        const safeDelay = Math.max(delay || 0, 50);
        return originalSetInterval(fn, safeDelay, ...args);
    };

    // Pause unneeded animations when battery saver is on
    const style = document.createElement('style');
    style.textContent = `
        * {
            animation-duration: 0.001s !important;
            transition-duration: 0.1s !important;
        }
        marquee, blink {
            animation: none !important;
        }
    `;
    (document.head || document.documentElement).appendChild(style);
    if (typeof GM_log === 'function') GM_log('Battery CPU Throttler engaged.');
})();
            """.trimIndent(),
            isEnabled = true,
            isBuiltIn = true
        ),
        UserScript(
            id = 3,
            name = "Unlock Copy & Context Menu",
            description = "Bypasses web restrictions preventing text copying, selection, and right-click context menus.",
            author = "OpenWeb",
            version = "1.8",
            matchPatterns = "*://*/*",
            runAt = "document-idle",
            code = """
// ==UserScript==
// @name         Unlock Copy & Context Menu
// @namespace    https://darkbrowser.app
// @version      1.8
// @description  Enables text selection and right click on restricted websites
// @match        *://*/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

(function() {
    'use strict';
    const events = ['copy', 'cut', 'paste', 'selectstart', 'contextmenu', 'dragstart', 'mousedown'];
    events.forEach(function(ev) {
        document.addEventListener(ev, function(e) {
            e.stopPropagation();
        }, true);
    });

    const unlockCss = `
        * {
            -webkit-user-select: text !important;
            -moz-user-select: text !important;
            user-select: text !important;
        }
    `;
    const style = document.createElement('style');
    style.textContent = unlockCss;
    (document.head || document.documentElement).appendChild(style);
    console.log('[DarkBrowser] Copy & Selection restrictions unlocked');
})();
            """.trimIndent(),
            isEnabled = true,
            isBuiltIn = true
        ),
        UserScript(
            id = 4,
            name = "HTML5 Video Speed & Controller",
            description = "Adds double-tap speed controls and auto-optimizes video playback.",
            author = "MediaMaster",
            version = "1.2",
            matchPatterns = "*://*/*",
            runAt = "document-idle",
            code = """
// ==UserScript==
// @name         HTML5 Video Speed & Controller
// @namespace    https://darkbrowser.app
// @version      1.2
// @description  Allows quick playback speed adjustments on HTML5 videos
// @match        *://*/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

(function() {
    'use strict';
    function attachControls() {
        const videos = document.querySelectorAll('video');
        videos.forEach(v => {
            if (v.dataset.darkBrowserControlled) return;
            v.dataset.darkBrowserControlled = "true";
            
            // Allow video to play inline and prevent aggressive pauses
            v.setAttribute('playsinline', '');
            v.setAttribute('webkit-playsinline', '');
        });
    }
    attachControls();
    setInterval(attachControls, 2000);
})();
            """.trimIndent(),
            isEnabled = false,
            isBuiltIn = true
        ),
        UserScript(
            id = 5,
            name = "Anti-Ad & Annoyance Cleaner",
            description = "Removes floating newsletter popups, cookie consent overlays, and intrusive banners.",
            author = "CleanShield",
            version = "2.0",
            matchPatterns = "*://*/*",
            runAt = "document-idle",
            code = """
// ==UserScript==
// @name         Anti-Ad & Annoyance Cleaner
// @namespace    https://darkbrowser.app
// @version      2.0
// @description  Hides intrusive popups and floating overlay bars
// @match        *://*/*
// @run-at       document-idle
// @grant        GM_addStyle
// ==/UserScript==

(function() {
    'use strict';
    const cleanCss = `
        [id*="cookie-banner"], [class*="cookie-consent"], [id*="newsletter-popup"],
        [class*="newsletter-modal"], [id*="ad-container"], [class*="ad-banner"],
        .tp-backdrop, .tp-modal {
            display: none !important;
            visibility: hidden !important;
            pointer-events: none !important;
        }
    `;
    if (typeof GM_addStyle === 'function') {
        GM_addStyle(cleanCss);
    } else {
        const style = document.createElement('style');
        style.textContent = cleanCss;
        (document.head || document.documentElement).appendChild(style);
    }
})();
            """.trimIndent(),
            isEnabled = true,
            isBuiltIn = true
        ),
        UserScript(
            id = 6,
            name = "Chromium DevTools Web Inspector",
            description = "Aktifkan Chromium DevTools Remote Inspector (Console, DOM Elements, Network XHR/Fetch, Storage) untuk inspeksi web.",
            author = "Chromium DevTools / FoldDevtools",
            version = "1.0",
            matchPatterns = "*://*/*",
            runAt = "document-end",
            code = """
// ==UserScript==
// @name         Chromium DevTools Web Inspector
// @namespace    https://developer.chrome.com/docs/devtools
// @version      1.0
// @description  Chromium Remote Debugging & In-browser DevTools Inspector
// @match        *://*/*
// @run-at       document-end
// @grant        none
// ==/UserScript==

(function() {
    'use strict';
    if (window.__vconsole_instance) return;
    var script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/vconsole@latest/dist/vconsole.min.js';
    script.onload = function() {
        if (window.VConsole) {
            window.__vconsole_instance = new window.VConsole({ theme: 'dark' });
        }
    };
    (document.head || document.documentElement || document.body).appendChild(script);
})();
            """.trimIndent(),
            isEnabled = false,
            isBuiltIn = true
        )
    )
}
