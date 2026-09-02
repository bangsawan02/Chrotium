package com.example.engine

import android.os.Build
import kotlinx.coroutines.launch

object WebConfig {
    /**
     * User-Agent Chrome 150.0.7871.186 sesuai spesifikasi peramban.
     */
    const val CHROME_VERSION = "150.0.7871.186"
    const val CHROME_VERSION_CODE = 787118633
    const val PACKAGE_NAME = "com.android.chrome"

    fun getMobileUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.102 Mobile Safari/537.36"
    }

    fun getDesktopUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }

    /**
     * Mengembalikan User-Agent khusus performa tinggi sesuai situs:
     * - YouTube: Selalu gunakan Mobile UA agar memuat bundle JS Polymer yang lebih ringan.
     * - Google AI Studio: Selalu gunakan Desktop UA agar editor kode & layout bekerja sempurna.
     * - Google Auth: UA aman bebas blokir.
     * - Default: Tergantung status isDesktopMode.
     */
    fun getCustomUserAgent(url: String, isDesktopMode: Boolean): String {
        if (url.isBlank()) {
            return if (isDesktopMode) getDesktopUserAgent() else getMobileUserAgent()
        }
        val lowerUrl = url.lowercase()
        return when {
            isGoogleAuthUrl(url) -> getGoogleAuthUserAgent(isDesktopMode)
            lowerUrl.contains("aistudio.google.com") -> getDesktopUserAgent()
            lowerUrl.contains("youtube.com") -> getMobileUserAgent()
            isDesktopMode -> getDesktopUserAgent()
            else -> getMobileUserAgent()
        }
    }

    /**
     * User-Agent khusus otentikasi Google / OAuth agar tidak memicu "disallowed_useragent" (browser not secure).
     * Menggunakan User-Agent resmi Google Chrome Android tanpa identifier WebView.
     */
    fun getGoogleAuthUserAgent(isDesktopMode: Boolean = false): String {
        return if (isDesktopMode) {
            getDesktopUserAgent()
        } else {
            getMobileUserAgent()
        }
    }

    /**
     * Memeriksa apakah URL merupakan halaman otentikasi Google / OAuth / Identity Services
     */
    fun isGoogleAuthUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains("accounts.google.") ||
               lower.contains("accounts.youtube.com") ||
               lower.contains("oauth2.googleapis.com") ||
               lower.contains("myaccount.google.com") ||
               lower.contains("gds.google.com") ||
               lower.contains("apis.google.com") ||
               lower.contains("accounts.google.com/gsi") ||
               lower.contains("accounts.google.com/o/oauth2") ||
               lower.contains("accounts.google.com/signin") ||
               lower.contains("accounts.google.com/v3/signin") ||
               lower.contains("accounts.google.com/serviceauth")
    }

    /**
     * Script untuk mematikan SEMUA smooth scrolling di seluruh elemen, dokumen, dan API window/element
     * agar scrolling instan dan responsif tanpa lag/animasi penundaan.
     */
    val DISABLE_SMOOTH_SCROLLING_SCRIPT = """
        (function() {
            if (window.__chrotium_disable_smooth_scroll) return;
            window.__chrotium_disable_smooth_scroll = true;
            try {
                // 1. Matikan CSS scroll-behavior smooth secara paksa di semua elemen
                var style = document.createElement('style');
                style.id = 'chrotium-no-smooth-scroll';
                style.textContent = `
                    *, html, body, :root {
                        scroll-behavior: auto !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);

                // 2. Intercept Element.prototype.scrollIntoView agar behavior selalu 'auto' / 'instant'
                if (Element.prototype.scrollIntoView) {
                    var origScrollIntoView = Element.prototype.scrollIntoView;
                    Element.prototype.scrollIntoView = function(arg) {
                        if (arg && typeof arg === 'object') {
                            arg.behavior = 'auto';
                            return origScrollIntoView.call(this, arg);
                        }
                        return origScrollIntoView.call(this, arg);
                    };
                }

                // 3. Intercept window.scrollTo, window.scroll, window.scrollBy
                function stripSmooth(fn) {
                    return function() {
                        if (arguments.length === 1 && typeof arguments[0] === 'object' && arguments[0] !== null) {
                            arguments[0].behavior = 'auto';
                        }
                        return fn.apply(this, arguments);
                    };
                }
                if (window.scrollTo) window.scrollTo = stripSmooth(window.scrollTo);
                if (window.scroll) window.scroll = stripSmooth(window.scroll);
                if (window.scrollBy) window.scrollBy = stripSmooth(window.scrollBy);

                // 4. Pastikan form input, textarea, and contenteditable selalu auto-scroll ke area terlihat saat keyboard aktif
                if (!window.__chrotium_focus_listener) {
                    window.__chrotium_focus_listener = true;
                    document.addEventListener('focusin', function(e) {
                        var el = e.target;
                        if (el && (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT' || el.isContentEditable)) {
                            setTimeout(function() {
                                try {
                                    el.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'auto' });
                                } catch(err) {
                                    try { el.scrollIntoView(false); } catch(err2) {}
                                }
                            }, 100);
                        }
                    }, true);
                }
            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * Script client-side untuk memastikan viewport dan navigator web selalu
     * menampilkan layout layar seluler (mobile screen) yang responsif dan proporsional.
     */
    val MOBILE_VIEWPORT_SCRIPT = """
        (function() {
            try {
                // 1. Force responsive mobile viewport meta tag
                function setMobileViewport() {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (meta) {
                        var content = meta.getAttribute('content') || '';
                        if (content.indexOf('width=device-width') === -1 || content.indexOf('initial-scale=1') === -1) {
                            meta.setAttribute('content', 'width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=5.0, user-scalable=yes, viewport-fit=cover');
                        }
                    } else {
                        var newMeta = document.createElement('meta');
                        newMeta.name = 'viewport';
                        newMeta.content = 'width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=5.0, user-scalable=yes, viewport-fit=cover';
                        (document.head || document.documentElement).appendChild(newMeta);
                    }
                }
                setMobileViewport();
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', setMobileViewport);
                }

                // Inject overflow containment styles for responsive images & text wrapping
                var overflowStyle = document.createElement('style');
                overflowStyle.id = 'chrotium-anti-overflow';
                overflowStyle.textContent = `
                    img, video {
                        max-width: 100%;
                    }
                    p, h1, h2, h3, h4, h5, h6, blockquote {
                        overflow-wrap: break-word;
                    }
                `;
                (document.head || document.documentElement).appendChild(overflowStyle);

                // 2. Enforce navigator.userAgentData mobile flag (Identik dengan Google Chrome Android murni)
                if (navigator.userAgentData) {
                    try {
                        Object.defineProperty(navigator, 'userAgentData', {
                            get: function() {
                                return {
                                    mobile: true,
                                    platform: 'Android',
                                    brands: [
                                        { brand: 'Chromium', version: '130' },
                                        { brand: 'Google Chrome', version: '130' },
                                        { brand: 'Not?A_Brand', version: '99' }
                                    ]
                                };
                            },
                            configurable: true
                        });
                    } catch(e) {}
                }

                // 3. Enforce touch capability
                if (!('ontouchstart' in window) && (navigator.maxTouchPoints === 0 || !navigator.maxTouchPoints)) {
                    try {
                        Object.defineProperty(navigator, 'maxTouchPoints', {
                            get: function() { return 5; },
                            configurable: true
                        });
                    } catch(e) {}
                }
            } catch(err) {
                console.error('[Chrotium] Mobile viewport injection error:', err);
            }
        })();
    """.trimIndent()

    /**
     * Script client-side untuk memaksa viewport dan navigator web agar selalu
     * menampilkan layout desktop asli secara sempurna.
     */
    val DESKTOP_VIEWPORT_SCRIPT = """
        (function() {
            try {
                // 1. Force desktop viewport meta tag (1280px standard desktop canvas)
                function setDesktopViewport() {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (meta) {
                        meta.setAttribute('content', 'width=1280, initial-scale=0.35, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes');
                    } else {
                        var newMeta = document.createElement('meta');
                        newMeta.name = 'viewport';
                        newMeta.content = 'width=1280, initial-scale=0.35, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes';
                        (document.head || document.documentElement).appendChild(newMeta);
                    }
                }
                setDesktopViewport();
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', setDesktopViewport);
                }

                // 2. Override navigator.userAgentData
                if (navigator.userAgentData) {
                    try {
                        Object.defineProperty(navigator, 'userAgentData', {
                            get: function() {
                                return {
                                    mobile: false,
                                    platform: 'Windows',
                                    brands: [
                                        { brand: 'Chromium', version: '130' },
                                        { brand: 'Google Chrome', version: '130' },
                                        { brand: 'Not?A_Brand', version: '99' }
                                    ]
                                };
                            },
                            configurable: true
                        });
                    } catch(e) {}
                }

                // 3. Override navigator.platform and maxTouchPoints for true desktop layout rendering
                try {
                    Object.defineProperty(navigator, 'platform', {
                        get: function() { return 'Win32'; },
                        configurable: true
                    });
                } catch(e) {}

                try {
                    Object.defineProperty(navigator, 'maxTouchPoints', {
                        get: function() { return 0; },
                        configurable: true
                    });
                } catch(e) {}
            } catch(err) {
                console.error('[Chrotium] Desktop mode injection failed:', err);
            }
        })();
    """.trimIndent()

    /**
     * Script untuk memungkinkan pemutaran audio/video di latar belakang (Background Play).
     * Memalsukan Page Visibility API agar YouTube, Spotify Web, dan situs media lainnya
     * tidak pernah dijeda saat aplikasi diminimalkan atau layar dimatikan.
     */
    val BACKGROUND_PLAY_SCRIPT = """
        (function() {
            if (window.__chrotium_bg_play_injected) return;
            window.__chrotium_bg_play_injected = true;

            try {
                // Track application foreground/background state
                window.__chrotium_is_background = false;

                // 1. Force Page Visibility API & Document Focus to always report visible/focused
                try {
                    Object.defineProperty(document, 'hidden', {
                        get: function() { return false; },
                        configurable: true
                    });
                    Object.defineProperty(document, 'visibilityState', {
                        get: function() { return 'visible'; },
                        configurable: true
                    });
                    Object.defineProperty(document, 'webkitHidden', {
                        get: function() { return false; },
                        configurable: true
                    });
                    Object.defineProperty(document, 'webkitVisibilityState', {
                        get: function() { return 'visible'; },
                        configurable: true
                    });
                    document.hasFocus = function() { return true; };
                } catch(e) {}

                // 2. Stop visibilitychange & freeze propagation so YouTube doesn't pause video in background
                document.addEventListener('visibilitychange', function(e) { 
                    window.__chrotium_is_background = true;
                    if (e) e.stopImmediatePropagation(); 
                }, true);
                document.addEventListener('webkitvisibilitychange', function(e) { 
                    window.__chrotium_is_background = true;
                    if (e) e.stopImmediatePropagation(); 
                }, true);
                window.addEventListener('pagehide', function(e) { 
                    window.__chrotium_is_background = true;
                    if (e) e.stopImmediatePropagation(); 
                }, true);

                // 3. Ensure HTMLMediaElement.prototype.play resolves cleanly during YouTube Mix / Playlist auto-advance in background
                var origPlay = HTMLMediaElement.prototype.play;
                HTMLMediaElement.prototype.play = function() {
                    var promise = origPlay.apply(this, arguments);
                    if (promise && typeof promise.catch === 'function') {
                        promise.catch(function(err) {});
                    }
                    return promise;
                };

                // 4. Intercept HTMLMediaElement.prototype.pause to completely ignore pause requests while in the background
                var origPause = HTMLMediaElement.prototype.pause;
                HTMLMediaElement.prototype.pause = function() {
                    if (window.__chrotium_is_background) {
                        return Promise.resolve ? Promise.resolve() : undefined;
                    }
                    return origPause.apply(this, arguments);
                };
            } catch(e) {
                console.error('[Chrotium] Background Play injection error:', e);
            }
        })();
    """.trimIndent()

    /**
     * Script Optimasi Ringan Khusus untuk Framework Modern SPA & Web Apps (Google AI Studio, Angular, React, Vue, Svelte):
     * - Eliminasi 300ms tap delay untuk responsivitas seketika.
     * - Router & SPA URL Synchronization yang efisien tanpa membebani DOM atau CPU.
     * - Tidak menggunakan subtree MutationObserver agar situs berat (seperti ai.studio) berjalan 60FPS lancar.
     */
    val ANGULAR_SPA_OPTIMIZATION_SCRIPT = """
        (function() {
            if (window.__chrotium_angular_spa_injected) return;
            window.__chrotium_angular_spa_injected = true;

            try {
                // 1. Injeksi CSS Ringan untuk Touch Response & Kelancaran Scroll
                var style = document.createElement('style');
                style.id = 'chrotium-angular-spa-style';
                style.textContent = `
                    /* Eliminasi 300ms tap delay untuk responsivitas tombol seketika (0ms click latency) */
                    button, a, input, select, textarea, [role="button"], [role="tab"], [role="menuitem"],
                    [mat-button], [mat-icon-button], [mat-raised-button], [mat-flat-button], [mat-stroked-button],
                    .mat-mdc-button-base, mat-select, mat-checkbox, mat-radio-button, mat-slide-toggle,
                    .p-button, .p-dropdown, .p-checkbox, .p-radiobutton,
                    .v-btn, .v-list-item, .v-select,
                    ion-button, ion-item, ion-checkbox, ion-toggle, ion-segment-button {
                        touch-action: manipulation !important;
                        -webkit-tap-highlight-color: transparent !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);

                // 2. Zone.js & Angular Change Detection Compatibility Guards
                if (typeof window !== 'undefined') {
                    window.__Zone_disable_on_property = false;
                    window.__Zone_enable_cross_context_check = true;
                }

                // 3. Jembatan Clipboard API untuk Tombol Salin & Tempel (AI Studio, GitHub, Monaco Editor)
                if (navigator.clipboard) {
                    var originalWriteText = navigator.clipboard.writeText;
                    navigator.clipboard.writeText = function(text) {
                        try {
                            if (window.ChrotiumInterface && typeof window.ChrotiumInterface.copyToClipboard === 'function') {
                                window.ChrotiumInterface.copyToClipboard(String(text));
                            }
                        } catch(e) {}
                        if (typeof originalWriteText === 'function') {
                            return originalWriteText.apply(navigator.clipboard, arguments).catch(function() {
                                return Promise.resolve();
                            });
                        }
                        return Promise.resolve();
                    };

                    var originalReadText = navigator.clipboard.readText;
                    navigator.clipboard.readText = function() {
                        if (window.ChrotiumInterface && typeof window.ChrotiumInterface.getClipboardText === 'function') {
                            try {
                                var clipText = window.ChrotiumInterface.getClipboardText();
                                if (clipText !== undefined && clipText !== null) {
                                    return Promise.resolve(clipText);
                                }
                            } catch(e) {}
                        }
                        if (typeof originalReadText === 'function') {
                            return originalReadText.apply(navigator.clipboard, arguments).catch(function() {
                                return Promise.resolve("");
                            });
                        }
                        return Promise.resolve("");
                    };
                }

                // 4. Real-Time SPA Router & URL Synchronization yang sangat efisien
                var lastReportedUrl = location.href;
                var lastReportedTitle = document.title;
                var syncTimeout = null;

                function reportUrlChange() {
                    try {
                        var curUrl = location.href;
                        var curTitle = document.title || curUrl;
                        if (curUrl && curUrl !== 'about:blank' && (curUrl !== lastReportedUrl || curTitle !== lastReportedTitle)) {
                            lastReportedUrl = curUrl;
                            lastReportedTitle = curTitle;
                            if (window.TampermonkeyBridge && typeof window.TampermonkeyBridge.onSpaUrlChanged === 'function') {
                                window.TampermonkeyBridge.onSpaUrlChanged(curUrl, curTitle);
                            }
                            if (window.ChrotiumInterface && typeof window.ChrotiumInterface.onUrlChange === 'function') {
                                window.ChrotiumInterface.onUrlChange(curUrl);
                            }
                        }
                    } catch(e) {}
                }

                function scheduleUrlCheck() {
                    if (syncTimeout) clearTimeout(syncTimeout);
                    syncTimeout = setTimeout(reportUrlChange, 50);
                }

                function hookHistoryMethod(method) {
                    var original = history[method];
                    if (typeof original === 'function') {
                        history[method] = function() {
                            var result = original.apply(this, arguments);
                            scheduleUrlCheck();
                            return result;
                        };
                    }
                }
                hookHistoryMethod('pushState');
                hookHistoryMethod('replaceState');

                // Framework-specific router event hooks
                window.addEventListener('popstate', scheduleUrlCheck, true);
                window.addEventListener('hashchange', scheduleUrlCheck, true);
                
                // YouTube, Next.js, Nuxt, Turbo, HTMX hooks
                window.addEventListener('yt-navigate-finish', scheduleUrlCheck, true);
                window.addEventListener('turbo:load', scheduleUrlCheck, true);
                window.addEventListener('turbo:render', scheduleUrlCheck, true);
                window.addEventListener('turbolinks:load', scheduleUrlCheck, true);
                window.addEventListener('htmx:pushedIntoHistory', scheduleUrlCheck, true);
                window.addEventListener('htmx:afterSettle', scheduleUrlCheck, true);

                // Pantau elemen <title> saja secara spesifik (Nol overhead untuk DOM)
                var titleEl = document.querySelector('title');
                if (titleEl) {
                    var titleObserver = new MutationObserver(scheduleUrlCheck);
                    titleObserver.observe(titleEl, { childList: true, characterData: true });
                }

                // Polling santai setiap 2.5 detik
                setInterval(reportUrlChange, 2500);

                // 4. Sinkronisasi status media berkala
                function checkMediaStatus() {
                    try {
                        var isPlaying = false;
                        var mediaElements = document.querySelectorAll('video, audio');
                        for (var i = 0; i < mediaElements.length; i++) {
                            var m = mediaElements[i];
                            if (m && !m.paused && m.currentTime > 0 && !m.ended && m.readyState > 2) {
                                isPlaying = true;
                                break;
                            }
                        }
                        if (window.ChrotiumInterface && typeof window.ChrotiumInterface.updateMediaStatus === 'function') {
                            window.ChrotiumInterface.updateMediaStatus(isPlaying);
                        }
                    } catch(e) {}
                }
                setInterval(checkMediaStatus, 3000);

            } catch(err) {
                console.error('[Chrotium] Angular & SPA optimization error:', err);
            }
        })();
    """.trimIndent()

    /**
     * Script Optimasi Khusus Google AI Studio (ai.studio & aistudio.google.com):
     * - Full bi-directional Clipboard API & ClipboardItem polyfill.
     * - Permissions API Query bypass ('clipboard-read', 'clipboard-write', 'microphone').
     * - Persistent Storage Guarantee (navigator.storage.persist) untuk draft prompt & model cache.
     * - Monaco Editor & Prompt Input mobile touch & scrolling optimization (no double-tap zoom glitch, no jitter).
     * - Run Shortcut Assist (Ctrl+Enter / Cmd+Enter untuk trigger prompt run).
     * - Zero-Lag Markdown & Code Token Streaming Optimization (CSS contain & GPU accelerated text flow).
     */
    val AI_STUDIO_OPTIMIZATION_SCRIPT = """
        (function() {
            if (window.__chrotium_ai_studio_injected) return;
            window.__chrotium_ai_studio_injected = true;

            try {
                // 1. Injeksi Style CSS Khusus Google AI Studio Workspace
                var style = document.createElement('style');
                style.id = 'chrotium-ai-studio-style';
                style.textContent = `
                    /* Monaco Editor & Prompt Textarea - Sentuhan halus & scrolling lancar */
                    .monaco-editor,
                    .monaco-editor .monaco-scrollable-element,
                    .prompt-input,
                    textarea.inputarea,
                    [role="textbox"] {
                        touch-action: pan-x pan-y !important;
                        overscroll-behavior-y: contain !important;
                        -webkit-user-select: text !important;
                        user-select: text !important;
                    }

                    /* Akselerasi GPU untuk container respon token streaming Gemini */
                    .chat-message,
                    .model-turn,
                    .user-turn,
                    .rendered-markdown,
                    .code-block,
                    pre code {
                        contain: content !important;
                        -webkit-overflow-scrolling: touch !important;
                        transform: translateZ(0);
                    }

                    /* Cegah horizontal layout break pada prompt & sidebar panel */
                    .system-instructions,
                    .safety-settings,
                    .tuning-controls,
                    .sidebar-content {
                        overflow-y: auto !important;
                        -webkit-overflow-scrolling: touch !important;
                    }

                    /* Tombol Run & Tombol Salin Cepat */
                    .run-button,
                    [data-test-id="run-button"],
                    button[aria-label*="Run"],
                    button[aria-label*="Copy"] {
                        touch-action: manipulation !important;
                        -webkit-tap-highlight-color: transparent !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);

                // 2. Storage Persistence untuk Draft Prompt & Model Cache
                if (navigator.storage && typeof navigator.storage.persist === 'function') {
                    navigator.storage.persist().catch(function() {});
                }

                // 3. Permissions API Query Auto-Grant (Clipboard & Audio Capture)
                if (navigator.permissions && typeof navigator.permissions.query === 'function') {
                    var origQuery = navigator.permissions.query;
                    navigator.permissions.query = function(params) {
                        if (params && (params.name === 'clipboard-read' || params.name === 'clipboard-write' || params.name === 'microphone')) {
                            return Promise.resolve({
                                state: 'granted',
                                name: params.name,
                                onchange: null,
                                addEventListener: function() {},
                                removeEventListener: function() {},
                                dispatchEvent: function() { return false; }
                            });
                        }
                        return origQuery.apply(navigator.permissions, arguments);
                    };
                }

                // 4. Shortcut Keyboard Assist: Ctrl+Enter / Cmd+Enter untuk trigger 'Run' Prompt
                window.addEventListener('keydown', function(e) {
                    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                        var runBtn = document.querySelector('button[aria-label*="Run"]') ||
                                     document.querySelector('[data-test-id="run-button"]') ||
                                     document.querySelector('.run-button') ||
                                     document.querySelector('button.mat-primary');
                        if (runBtn && typeof runBtn.click === 'function') {
                            e.preventDefault();
                            e.stopPropagation();
                            runBtn.click();
                        }
                    }
                }, true);

            } catch(e) {
                console.error('[Chrotium] AI Studio script error:', e);
            }
        })();
    """.trimIndent()

    /**
     * Script global untuk mengoptimalkan rendering semua elemen video HTML5
     * di berbagai situs web. Memaksa komposit GPU agar tidak patah-patah.
     */
    val GLOBAL_VIDEO_PERFORMANCE_SCRIPT = """
        (function() {
            if (window.__chrotium_global_vid_perf_injected) return;
            window.__chrotium_global_vid_perf_injected = true;
            try {
                var style = document.createElement('style');
                style.id = 'chrotium-global-vid-perf-style';
                style.textContent = `
                    video {
                        transform: translate3d(0, 0, 0) !important;
                        -webkit-transform: translate3d(0, 0, 0) !important;
                        will-change: transform, opacity !important;
                        backface-visibility: hidden !important;
                        -webkit-backface-visibility: hidden !important;
                        image-rendering: -webkit-optimize-contrast !important;
                        image-rendering: crisp-edges !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * Script khusus optimasi YouTube untuk pengalaman maksimal:
     * - Memaksa compositing GPU hardware acceleration pada elemen video dan UI.
     * - Menonaktifkan Ambient Mode / efek pencahayaan latar belakang YouTube yang membebankan rendering GPU.
     * - Menambahkan gestur Double Tap to Seek (mundur/maju 10 detik).
     * - Otomatis menutup dialog "Tetap menonton?" / "Still watching?" tanpa jeda.
     * - Mengaktifkan MediaSession API (Judul, Channel, Thumbnail, Kontrol Musik).
     * - Mengoptimalkan buffer streaming video untuk 60FPS / 4K tanpa frame drops.
     */
    val YOUTUBE_PERFORMANCE_SCRIPT = """
        (function() {
            if (window.__chrotium_yt_perf_injected) return;
            window.__chrotium_yt_perf_injected = true;

            try {
                // 1. Inject Hardware Acceleration CSS rules & UI cleanups
                var style = document.createElement('style');
                style.id = 'chrotium-yt-perf-style';
                style.textContent = `
                    video, .html5-main-video, .video-stream {
                        transform: translate3d(0, 0, 0) !important;
                        -webkit-transform: translate3d(0, 0, 0) !important;
                        will-change: transform !important;
                        backface-visibility: hidden !important;
                        -webkit-backface-visibility: hidden !important;
                        image-rendering: -webkit-optimize-contrast !important;
                        image-rendering: crisp-edges !important;
                    }
                    /* Fix YouTube Mobile Mix / Playlist Panel & Settings Dialog Scrolling */
                    ytm-playlist-panel-renderer, 
                    .playlist-panel, 
                    ytm-engagement-panel-section-list-renderer, 
                    #playlist-items {
                        overflow-y: auto !important;
                        -webkit-overflow-scrolling: touch !important;
                        pointer-events: auto !important;
                        touch-action: pan-y !important;
                    }

                    /* Ensure YouTube Video Settings, Quality & Menu Dialogs are always on top and clickable */
                    .ytp-settings-menu,
                    .ytp-popup,
                    .ytp-panel,
                    .ytp-panel-menu,
                    .ytp-settings-button,
                    .ytp-button,
                    ytm-menu-popup-renderer,
                    .ytm-menu-popup-renderer,
                    ytm-custom-dialog-renderer,
                    ytm-bottom-sheet-renderer,
                    ytm-single-select-dialog-renderer,
                    ytm-multi-select-dialog-renderer,
                    ytm-dialog-renderer,
                    .dialog-container,
                    .bottom-sheet-container,
                    .bottom-sheet-layout,
                    .cbox {
                        visibility: visible !important;
                        opacity: 1 !important;
                        pointer-events: auto !important;
                        touch-action: manipulation !important;
                        z-index: 2147483647 !important;
                    }

                    ytm-menu-item,
                    .ytm-menu-item,
                    .ytp-menuitem,
                    .ytp-panel-menu .ytp-menuitem,
                    ytm-single-select-dialog-renderer .dialog-content,
                    ytm-bottom-sheet-renderer .bottom-sheet-content,
                    .dialog-content,
                    .bottom-sheet-content {
                        pointer-events: auto !important;
                        touch-action: manipulation !important;
                    }

                    /* Disable heavy YouTube Ambient Mode / Cinematics effect causing GPU lag */
                    #cinematics, ytd-watch-flexy[ambient-mode] #cinematics, .ytp-gradient-bottom, .ytp-gradient-top {
                        display: none !important;
                        opacity: 0 !important;
                        visibility: hidden !important;
                    }
                    /* Visual feedback for double tap seek */
                    .chrotium-seek-ripple {
                        position: absolute;
                        top: 50%;
                        transform: translateY(-50%);
                        background: rgba(255, 255, 255, 0.35);
                        color: #ffffff;
                        font-weight: bold;
                        font-size: 14px;
                        padding: 8px 14px;
                        border-radius: 20px;
                        pointer-events: none;
                        z-index: 9999;
                        animation: chrotium-fade 0.6s ease-out forwards;
                    }
                    @keyframes chrotium-fade {
                        0% { opacity: 1; transform: translateY(-50%) scale(0.9); }
                        100% { opacity: 0; transform: translateY(-50%) scale(1.15); }
                    }
                `;
                (document.head || document.documentElement).appendChild(style);

                // 2. Optimize YouTube Player performance configuration if present
                if (window.yt && window.yt.config_) {
                    window.yt.config_.EXPERIMENT_FLAGS = window.yt.config_.EXPERIMENT_FLAGS || {};
                    window.yt.config_.EXPERIMENT_FLAGS.web_enable_ab_testing = false;
                    window.yt.config_.EXPERIMENT_FLAGS.html5_enable_subframe_player = false;
                    window.yt.config_.EXPERIMENT_FLAGS.html5_player_audio_quality_experiment = false;
                    
                    // Chrome-like optimization: Disable quality auto-throttling & force smooth streaming
                    window.yt.config_.EXPERIMENT_FLAGS.html5_disable_auto_throttle = true;
                    window.yt.config_.EXPERIMENT_FLAGS.web_playback_use_request_animation_frame = true;
                    window.yt.config_.EXPERIMENT_FLAGS.html5_force_high_quality_formats = true;
                    window.yt.config_.EXPERIMENT_FLAGS.html5_enable_vpx_decoding = true;
                    window.yt.config_.EXPERIMENT_FLAGS.html5_enable_av1_decoding = true;
                }

                // Active video buffer preloading optimization
                function optimizeVideoPlayer() {
                    try {
                        var video = document.querySelector('video');
                        if (video) {
                            video.preload = 'auto';
                            // Ensure standard adaptive bitrate buffering remains active even on network shifts
                            if (typeof video.setAttribute === 'function') {
                                video.setAttribute('preload', 'auto');
                            }
                        }
                    } catch(e) {}
                }
                setInterval(optimizeVideoPlayer, 3000);

                // 3. Double-tap to seek on mobile YouTube player
                var lastTapTime = 0;
                var lastTapX = 0;
                document.addEventListener('touchend', function(e) {
                    try {
                        var target = e.target;
                        if (!target) return;
                        
                        // Ignore touches on settings, menus, and controls
                        if (target.closest('button, .ytp-button, .ytp-settings-button, .ytp-chrome-top, .ytp-chrome-bottom, .ytp-settings-menu, .ytp-popup, ytm-menu-popup-renderer, .ytm-menu-popup-renderer, ytm-custom-dialog-renderer, ytm-bottom-sheet-renderer, .ytm-menu-item, [role="menuitem"], [role="button"], [aria-label*="Setting"], [aria-label*="Setelan"], [aria-label*="Pengaturan"]')) {
                            return;
                        }

                        var video = document.querySelector('video');
                        if (!video) return;
                        var playerContainer = target.closest('#player-container-id, .player-container, #movie_player, .html5-video-player');
                        if (!playerContainer) return;

                        var currentTime = new Date().getTime();
                        var tapLength = currentTime - lastTapTime;
                        var touch = e.changedTouches[0];
                        if (!touch) return;

                        var rect = playerContainer.getBoundingClientRect();
                        var touchX = touch.clientX - rect.left;
                        var width = rect.width;

                        if (tapLength < 350 && tapLength > 50 && Math.abs(touch.clientX - lastTapX) < 60) {
                            // Double tap detected
                            var isRight = touchX > (width * 0.6);
                            var isLeft = touchX < (width * 0.4);
                            if (isRight) {
                                video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
                                showSeekIndicator(playerContainer, '+10s', 'right');
                                e.preventDefault();
                            } else if (isLeft) {
                                video.currentTime = Math.max(0, video.currentTime - 10);
                                showSeekIndicator(playerContainer, '-10s', 'left');
                                e.preventDefault();
                            }
                        }
                        lastTapTime = currentTime;
                        lastTapX = touch.clientX;
                    } catch(err) {}
                }, { passive: false });

                function showSeekIndicator(container, text, side) {
                    try {
                        var el = document.createElement('div');
                        el.className = 'chrotium-seek-ripple';
                        el.textContent = text;
                        if (side === 'right') {
                            el.style.right = '20%';
                        } else {
                            el.style.left = '20%';
                        }
                        container.appendChild(el);
                        setTimeout(function() {
                            if (el && el.parentNode) el.parentNode.removeChild(el);
                        }, 600);
                    } catch(err) {}
                }

                // 4. Auto-Confirm ONLY "Still watching? / Anda masih menonton?" Confirmation Dialogs
                setInterval(function() {
                    try {
                        var confirmBtns = document.querySelectorAll('yt-confirm-dialog-renderer #confirm-button button, ytd-popup-container yt-confirm-dialog-renderer #confirm-button, ytm-confirm-dialog-renderer #confirm-button button');
                        for (var i = 0; i < confirmBtns.length; i++) {
                            if (confirmBtns[i].offsetParent !== null) {
                                confirmBtns[i].click();
                            }
                        }
                    } catch(e) {}
                }, 2000);

            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * Script untuk mem-bypass dan menghilangkan jeda iklan YouTube:
     * - Menghapus adPlacements, playerAds, dan adSlots dari response player YouTube.
     * - Mempercepat dan melewati iklan video secara instan tanpa suara dalam 0.05 detik.
     * - Menutup prompt promo YouTube Premium / download app secara otomatis.
     */
    val YOUTUBE_AD_BYPASS_SCRIPT = """
        (function() {
            if (window.__chrotium_yt_ad_bypass_injected) return;
            window.__chrotium_yt_ad_bypass_injected = true;

            try {
                // 1. Intercept XMLHttpRequest (XHR) used by YouTube player API
                const OriginalXHR = window.XMLHttpRequest;
                function NewXHR() {
                    const xhr = new OriginalXHR();
                    let requestURL = '';
                    
                    const originalOpen = xhr.open;
                    xhr.open = function(method, url, ...args) {
                        requestURL = url ? url.toString() : '';
                        return originalOpen.apply(this, [method, url, ...args]);
                    };

                    const originalSend = xhr.send;
                    xhr.send = function(...args) {
                        if (requestURL.includes('/youtubei/v1/player') || requestURL.includes('/youtubei/v1/next')) {
                            const originalOnReadyStateChange = xhr.onreadystatechange;
                            xhr.onreadystatechange = function() {
                                if (xhr.readyState === 4 && xhr.status === 200) {
                                    try {
                                        const text = xhr.responseText;
                                        if (text && (text.includes('adPlacements') || text.includes('playerAds') || text.includes('adSlots'))) {
                                            const json = JSON.parse(text);
                                            if (json) {
                                                delete json.adPlacements;
                                                delete json.playerAds;
                                                delete json.adSlots;
                                                delete json.adPlacementRenderer;
                                                delete json.adLayoutLoggingData;
                                                delete json.adBreakHeartbeatParams;
                                                delete json.adPlaybackContextParams;
                                                
                                                Object.defineProperty(xhr, 'responseText', {
                                                    get: function() { return JSON.stringify(json); },
                                                    configurable: true
                                                });
                                                Object.defineProperty(xhr, 'response', {
                                                    get: function() { return JSON.stringify(json); },
                                                    configurable: true
                                                });
                                            }
                                        }
                                    } catch(e) {}
                                }
                                if (originalOnReadyStateChange) {
                                    originalOnReadyStateChange.apply(this, arguments);
                                }
                            };
                        }
                        return originalSend.apply(this, args);
                    };
                    return xhr;
                }
                NewXHR.prototype = OriginalXHR.prototype;
                window.XMLHttpRequest = NewXHR;

                // 2. Intercept fetch responses for player API
                const originalFetch = window.fetch;
                window.fetch = async function(resource, init) {
                    const response = await originalFetch.apply(this, arguments);
                    try {
                        const url = resource instanceof Request ? resource.url : (resource ? resource.toString() : '');
                        if (url.includes('/youtubei/v1/player') || url.includes('/youtubei/v1/next')) {
                            const clone = response.clone();
                            const json = await clone.json();
                            if (json) {
                                delete json.adPlacements;
                                delete json.playerAds;
                                delete json.adSlots;
                                delete json.adPlacementRenderer;
                                delete json.adLayoutLoggingData;
                                delete json.adBreakHeartbeatParams;
                                delete json.adPlaybackContextParams;
                                return new Response(JSON.stringify(json), {
                                    status: response.status,
                                    statusText: response.statusText,
                                    headers: response.headers
                                });
                            }
                        }
                    } catch(e) {}
                    return response;
                };

                // 3. Fast ad skipper, fast forwarder & promo cleaner
                function runAdSkipper() {
                    try {
                        // Skip video ads immediately
                        const isAdShowing = document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay');
                        const video = document.querySelector('video');
                        if (video && isAdShowing) {
                            video.muted = true;
                            video.playbackRate = 16.0;
                            if (isFinite(video.duration) && video.duration > 0) {
                                video.currentTime = video.duration;
                            } else {
                                video.currentTime = 99999;
                            }
                        }

                        // Click skip buttons
                        const skipButtons = document.querySelectorAll(
                            '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, button.ytp-ad-skip-button, .ytp-ad-overlay-close-button'
                        );
                        for (let i = 0; i < skipButtons.length; i++) {
                            if (skipButtons[i].offsetParent !== null) {
                                skipButtons[i].click();
                            }
                        }

                        // Remove ad banners and promo dialogs
                        const adSelectors = [
                            '.ytp-ad-overlay-container',
                            '.ytp-ad-module',
                            '.ytp-ad-progress',
                            'ytm-promoted-sparkles-web-renderer',
                            'ytm-compact-promoted-item-renderer',
                            'ytd-ad-slot-renderer',
                            'ytd-promoted-sparkles-web-renderer',
                            'ytd-promoted-video-renderer',
                            'ytm-paid-content-overlay-renderer',
                            'ytm-mealbar-promo-renderer',
                            'ytm-upsell-dialog-renderer',
                            'ytd-mealbar-promo-renderer',
                            'ytm-unlimited-offer-module-renderer'
                        ];
                        for (let s = 0; s < adSelectors.length; s++) {
                            const els = document.querySelectorAll(adSelectors[s]);
                            for (let j = 0; j < els.length; j++) {
                                els[j].remove();
                            }
                        }
                    } catch(e) {}

                    if (window.ytInitialPlayerResponse) {
                        delete window.ytInitialPlayerResponse.adPlacements;
                        delete window.ytInitialPlayerResponse.playerAds;
                        delete window.ytInitialPlayerResponse.adSlots;
                    }
                }

                // Run skipper on high-frequency interval & MutationObserver
                setInterval(runAdSkipper, 60);

                const adObserver = new MutationObserver(runAdSkipper);
                if (document.body || document.documentElement) {
                    adObserver.observe(document.body || document.documentElement, {
                        childList: true,
                        subtree: true
                    });
                }
            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * Script h264ify untuk memaksa YouTube menggunakan codec H.264 (AVC) dengan memblokir codec VP8, VP9, AV1, dan WebM.
     * Mengurangi pemakaian CPU dan menghemat daya baterai.
     */
    val YOUTUBE_H264IFY_SCRIPT = """
        (function() {
            if (window.__chrotium_h264ify_injected) return;
            window.__chrotium_h264ify_injected = true;

            try {
                // Intercept MediaSource.isTypeSupported
                const originalIsTypeSupported = window.MediaSource && window.MediaSource.isTypeSupported;
                if (originalIsTypeSupported) {
                    window.MediaSource.isTypeSupported = function(mimeType) {
                        if (typeof mimeType === 'string') {
                            const lowerType = mimeType.toLowerCase();
                            if (lowerType.includes('vp8') || lowerType.includes('vp9') || lowerType.includes('av01') || lowerType.includes('webm')) {
                                return false;
                            }
                        }
                        return originalIsTypeSupported.apply(this, arguments);
                    };
                }

                // Intercept HTMLMediaElement.prototype.canPlayType
                const originalCanPlayType = window.HTMLMediaElement && window.HTMLMediaElement.prototype.canPlayType;
                if (originalCanPlayType) {
                    window.HTMLMediaElement.prototype.canPlayType = function(mimeType) {
                        if (typeof mimeType === 'string') {
                            const lowerType = mimeType.toLowerCase();
                            if (lowerType.includes('vp8') || lowerType.includes('vp9') || lowerType.includes('av01') || lowerType.includes('webm')) {
                                return "";
                            }
                        }
                        return originalCanPlayType.apply(this, arguments);
                    };
                }
            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * Script untuk memastikan fokus input tetap stabil dan anti-flicker saat keyboard virtual muncul/hilang
     * pada SPA seperti Google AI Studio (Monaco Editor / prompt box) dan YouTube (komentar / chat).
     */
    val INPUT_SCROLL_FOCUS_SCRIPT = """
        (function() {
            if (window.__chrotium_input_scroll_injected) return;
            window.__chrotium_input_scroll_injected = true;

            var lastActiveEditable = null;
            var restoreFocusTimer = null;

            function isEditable(el) {
                if (!el) return false;
                var tag = el.tagName;
                return tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable || 
                       el.getAttribute('role') === 'textbox' || 
                       (el.classList && (el.classList.contains('inputarea') || el.classList.contains('monaco-editor')));
            }

            // Simpan referensi elemen input yang sedang aktif
            document.addEventListener('focusin', function(e) {
                if (isEditable(e.target)) {
                    lastActiveEditable = e.target;
                }
            }, true);

            // Jaga fokus agar tidak hilang/flicker saat window resize terjadi karena keyboard Android muncul
            window.addEventListener('resize', function() {
                var active = document.activeElement || lastActiveEditable;
                if (active && isEditable(active)) {
                    if (restoreFocusTimer) clearTimeout(restoreFocusTimer);
                    restoreFocusTimer = setTimeout(function() {
                        try {
                            if (document.activeElement !== active && isEditable(active)) {
                                active.focus({ preventScroll: true });
                            }
                        } catch(err) {}
                    }, 40);
                }
            }, { passive: true });

            // Pastikan sentuhan pada elemen input langsung mempertahankan fokus
            document.addEventListener('touchstart', function(e) {
                var target = e.target;
                if (isEditable(target)) {
                    lastActiveEditable = target;
                } else if (target && target.closest) {
                    var closestEditable = target.closest('input, textarea, [contenteditable="true"], [role="textbox"], .monaco-editor');
                    if (closestEditable) {
                        lastActiveEditable = closestEditable;
                    }
                }
            }, { passive: true });

            // Smooth viewport visibility scroll hanya jika input berada benar-benar di luar viewport
            var scrollTimeout = null;
            function scrollActiveIntoView(target) {
                if (scrollTimeout) clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(function() {
                    try {
                        var el = target || document.activeElement;
                        // Monaco editor dan inputarea mengatur scroll internalnya sendiri
                        if (el && isEditable(el) && !el.classList.contains('inputarea')) {
                            var rect = el.getBoundingClientRect();
                            var vh = window.innerHeight || document.documentElement.clientHeight;
                            if (rect.bottom > vh || rect.top < 0) {
                                el.scrollIntoView({ behavior: 'auto', block: 'nearest', inline: 'nearest' });
                            }
                        }
                    } catch(e) {}
                }, 120);
            }

            document.addEventListener('focusin', function(e) {
                if (isEditable(e.target)) {
                    scrollActiveIntoView(e.target);
                }
            }, true);
        })();
    """.trimIndent()

    /**
     * Script untuk menangkap klik download pada tautan file APK, biner, GitHub Release/Blob, atau elemen dengan atribut download/Blob URL.
     */
    val DOWNLOAD_LINK_INTERCEPTOR_SCRIPT = """
        (function() {
            if (window.__chrotium_download_interceptor_injected) return;
            window.__chrotium_download_interceptor_injected = true;

            const binaryExtensions = /\.(apk|zip|rar|7z|tar|gz|tgz|pdf|mp3|mp4|m4a|wav|flac|avi|mkv|iso|bin|dmg|exe|msi|deb|rpm|aar|jar|txt|json|csv|docx|xlsx|pptx)(\?.*)?$/i;

            // Memori penampung blob global untuk melangkahi batasan CSP dan pembatalan URL (Revoke)
            window.__chrotium_blobs = window.__chrotium_blobs || {};

            // Intersept URL.createObjectURL untuk menangkap objek Blob asli sebelum dilepas
            if (window.URL && typeof window.URL.createObjectURL === 'function') {
                const origCreate = window.URL.createObjectURL;
                window.URL.createObjectURL = function(obj) {
                    const url = origCreate.call(window.URL, obj);
                    if (obj instanceof Blob) {
                        try {
                            window.__chrotium_blobs[url] = obj;
                        } catch(e) {}
                    }
                    return url;
                };
            }

            // 1. Tunda revokeObjectURL agar pembacaan fetch Blob asinkron di WebView tidak terputus
            if (window.URL && typeof window.URL.revokeObjectURL === 'function') {
                const origRevoke = window.URL.revokeObjectURL;
                window.URL.revokeObjectURL = function(url) {
                    setTimeout(function() {
                        try { origRevoke.call(window.URL, url); } catch(e) {}
                    }, 30000);
                };
            }

            function sendBlobToNative(dataUrl, filename, mimeType) {
                if (window.ChrotiumInterface && typeof window.ChrotiumInterface.saveBlobDownload === 'function') {
                    window.ChrotiumInterface.saveBlobDownload(dataUrl, filename || 'download', mimeType || 'application/octet-stream');
                }
            }

            function processBlobUrl(blobUrl, filename, mimeHint) {
                try {
                    // Coba ambil langsung dari memori penampung (100% andal, bebas galat CSP)
                    var cachedBlob = window.__chrotium_blobs && window.__chrotium_blobs[blobUrl];
                    if (cachedBlob) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            var effectiveName = filename || 'download';
                            if (effectiveName.indexOf('.') === -1 && cachedBlob.type) {
                                if (cachedBlob.type.includes('android.package-archive')) effectiveName += '.apk';
                                else if (cachedBlob.type.includes('zip')) effectiveName += '.zip';
                                else if (cachedBlob.type.includes('pdf')) effectiveName += '.pdf';
                                else if (cachedBlob.type.includes('json')) effectiveName += '.json';
                                else if (cachedBlob.type.includes('text/plain')) effectiveName += '.txt';
                                else if (cachedBlob.type.includes('png')) effectiveName += '.png';
                                else if (cachedBlob.type.includes('jpeg')) effectiveName += '.jpg';
                            }
                            sendBlobToNative(reader.result, effectiveName, cachedBlob.type || mimeHint || 'application/octet-stream');
                        };
                        reader.readAsDataURL(cachedBlob);
                        return;
                    }

                    // Coba fetch blob dari memori browser jika tidak ada di map
                    fetch(blobUrl)
                        .then(function(res) {
                            if (!res.ok) throw new Error('Fetch status ' + res.status);
                            return res.blob();
                        })
                        .then(function(blob) {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                                var effectiveName = filename || 'download';
                                if (effectiveName.indexOf('.') === -1 && blob.type) {
                                    if (blob.type.includes('android.package-archive')) effectiveName += '.apk';
                                    else if (blob.type.includes('zip')) effectiveName += '.zip';
                                    else if (blob.type.includes('pdf')) effectiveName += '.pdf';
                                    else if (blob.type.includes('json')) effectiveName += '.json';
                                    else if (blob.type.includes('text/plain')) effectiveName += '.txt';
                                    else if (blob.type.includes('png')) effectiveName += '.png';
                                    else if (blob.type.includes('jpeg')) effectiveName += '.jpg';
                                }
                                sendBlobToNative(reader.result, effectiveName, blob.type || mimeHint || 'application/octet-stream');
                            };
                            reader.readAsDataURL(blob);
                        })
                        .catch(function(err) {
                            // Fallback menggunakan XMLHttpRequest jika fetch dibatasi di lingkungan tertentu
                            try {
                                var xhr = new XMLHttpRequest();
                                xhr.open('GET', blobUrl, true);
                                xhr.responseType = 'blob';
                                xhr.onload = function() {
                                    if (xhr.status === 200 || xhr.status === 0) {
                                        var blob = xhr.response;
                                        var reader = new FileReader();
                                        reader.onloadend = function() {
                                            sendBlobToNative(reader.result, filename || 'download', blob.type || mimeHint || 'application/octet-stream');
                                        };
                                        reader.readAsDataURL(blob);
                                    }
                                };
                                xhr.send();
                            } catch(xhrErr) {
                                console.error('[Chrotium] Blob XHR fallback error:', xhrErr);
                            }
                        });
                } catch(e) {
                    console.error('[Chrotium] processBlobUrl error:', e);
                }
            }

            // 2. Intercept programmatic a.click() & HTMLAnchorElement.prototype.click
            if (window.HTMLAnchorElement && window.HTMLAnchorElement.prototype.click) {
                const origAnchorClick = window.HTMLAnchorElement.prototype.click;
                window.HTMLAnchorElement.prototype.click = function() {
                    const href = this.getAttribute('href') || this.href;
                    if (href && (href.startsWith('blob:') || href.startsWith('data:'))) {
                        const filename = this.getAttribute('download') || this.download || "";
                        if (href.startsWith('data:')) {
                            sendBlobToNative(href, filename, '');
                        } else {
                            processBlobUrl(href, filename, "");
                        }
                        return;
                    }
                    return origAnchorClick.apply(this, arguments);
                };
            }

            // 3. Intercept dispatchEvent untuk Synthetic MouseEvent('click') pada elemen download
            const origDispatch = EventTarget.prototype.dispatchEvent;
            EventTarget.prototype.dispatchEvent = function(event) {
                if (event && event.type === 'click' && this.tagName === 'A') {
                    const href = this.getAttribute('href') || this.href;
                    if (href && (href.startsWith('blob:') || href.startsWith('data:'))) {
                        const filename = this.getAttribute('download') || this.download || "";
                        if (href.startsWith('data:')) {
                            sendBlobToNative(href, filename, '');
                        } else {
                            processBlobUrl(href, filename, "");
                        }
                        return true;
                    }
                }
                return origDispatch.apply(this, arguments);
            };

            // 4. Tangkap klik pada tombol dan link download
            document.addEventListener('click', function(e) {
                let target = e.target;
                while (target && target.tagName !== 'A' && target.tagName !== 'BUTTON') {
                    target = target.parentElement;
                }
                if (!target) return;

                const href = target.getAttribute('href') || target.dataset?.href || target.dataset?.downloadUrl;
                
                // Khusus tombol 'Download raw file' atau tombol dengan atribut data-testid pada GitHub Blob viewer
                const isGithubRawButton = target.getAttribute('data-testid') === 'download-raw-button' ||
                                          target.getAttribute('data-testid') === 'raw-button' ||
                                          (target.textContent && target.textContent.trim().toLowerCase().includes('download raw'));

                // Jika link adalah Blob URL atau Data URL
                if (href && (href.startsWith('blob:') || href.startsWith('data:'))) {
                    const filename = target.getAttribute('download') || target.download || "";
                    if (href.startsWith('data:')) {
                        sendBlobToNative(href, filename, '');
                    } else {
                        processBlobUrl(href, filename, "");
                    }
                    e.preventDefault();
                    e.stopPropagation();
                    return;
                }

                if (!href || href === '#' || href.startsWith('javascript:')) {
                    return;
                }

                // Jangan intercept navigasi halaman repository biasa GitHub (kecuali tombol raw atau biner)
                if (href.includes('github.com') && href.includes('/blob/') && !isGithubRawButton) {
                    return;
                }

                const hasDownloadAttr = target.hasAttribute('download') || (typeof target.getAttribute('download') === 'string');
                const isGithubDownload = href.includes('/releases/download/') ||
                                         href.includes('raw.githubusercontent.com') ||
                                         href.includes('/archive/refs/') ||
                                         href.includes('?raw=true') ||
                                         href.includes('/raw/') ||
                                         isGithubRawButton;
                const isBinary = binaryExtensions.test(href) && (!href.includes('/blob/') || isGithubRawButton);

                if (hasDownloadAttr || isGithubDownload || isBinary) {
                    try {
                        const absoluteUrl = new URL(href, window.location.href).href;
                        // Hindari intercept jika URL absolut mengarah ke blob view biasa tanpa intensi download
                        if (absoluteUrl.includes('github.com') && absoluteUrl.includes('/blob/') && !isGithubRawButton) {
                            return;
                        }
                        const filename = target.getAttribute('download') || "";
                        if (window.ChrotiumInterface && typeof window.ChrotiumInterface.triggerDownload === 'function') {
                            window.ChrotiumInterface.triggerDownload(absoluteUrl, filename);
                            e.preventDefault();
                            e.stopPropagation();
                        }
                    } catch(err) {}
                }
            }, true);
        })();
    """.trimIndent()

    /**
     * Script akselerasi rendering hardware GPU & subpixel compositing:
     * - Mengaktifkan GPU rasterization layer untuk rendering teks dan animasi yang lebih tajam & responsif.
     * - Mengoptimalkan scrolling FPS pada halaman panjang/SPA kompleks menggunakan subpixel anti-aliasing.
     */
    val GPU_RENDER_ACCELERATION_SCRIPT = """
        (function() {
            if (window.__chrotium_gpu_render_active) return;
            window.__chrotium_gpu_render_active = true;
            try {
                var style = document.createElement('style');
                style.id = 'chrotium-gpu-render-enhancer';
                style.textContent = `
                    html, body {
                        -webkit-font-smoothing: antialiased !important;
                        -moz-osx-font-smoothing: grayscale !important;
                        text-rendering: optimizeLegibility !important;
                    }
                    video, canvas {
                        transform: translateZ(0) !important;
                        backface-visibility: hidden !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * Konfigurasi WebSettings performa tinggi & kompatibilitas penuh untuk semua framework modern.
     */
    fun configureWebSettings(settings: android.webkit.WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.setGeolocationEnabled(true)
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.safeBrowsingEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setNeedInitialFocus(true)
        settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                @Suppress("DEPRECATION")
                settings.offscreenPreRaster = true
            } catch (_: Throwable) {}
        }
        settings.defaultTextEncodingName = "UTF-8"
        settings.standardFontFamily = "sans-serif"
        settings.sansSerifFontFamily = "sans-serif"
        settings.serifFontFamily = "serif"
        settings.fixedFontFamily = "monospace"
        settings.cursiveFontFamily = "cursive"
        settings.fantasyFontFamily = "fantasy"
        settings.minimumFontSize = 1
        settings.minimumLogicalFontSize = 1
        settings.defaultFontSize = 16
        settings.defaultFixedFontSize = 13
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = true

    }

    /**
     * Pre-warming DNS resolution asinkron untuk mempercepat first page load
     * Menggunakan DNS-over-HTTPS (DoH) via Cloudflare (1.1.1.1) & Google (8.8.8.8) untuk memangkas latensi & bypass ISP blockades
     */
    fun warmUpDnsAndNetwork() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val domains = listOf(
                "google.com",
                "www.google.com",
                "youtube.com",
                "www.youtube.com",
                "duckduckgo.com",
                "bing.com",
                "github.com",
                "aistudio.google.com"
            )
            for (domain in domains) {
                // 1. Sistem DNS Pre-warm standar
                try {
                    java.net.InetAddress.getAllByName(domain)
                } catch (_: Throwable) {}

                // 2. DoH Pre-warm via Cloudflare & Google untuk melatih layer jaringan dan melewati pemblokiran ISP
                try {
                    val urlCloudflare = java.net.URL("https://cloudflare-dns.com/dns-query?name=$domain&type=A")
                    val conn = urlCloudflare.openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("Accept", "application/dns-json")
                    conn.connectTimeout = 1500
                    conn.readTimeout = 1500
                    conn.inputStream.use { it.readBytes() }
                } catch (_: Throwable) {
                    // Jika Cloudflare terhambat, coba Google DoH
                    try {
                        val urlGoogle = java.net.URL("https://dns.google/resolve?name=$domain&type=A")
                        val conn = urlGoogle.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 1500
                        conn.readTimeout = 1500
                        conn.inputStream.use { it.readBytes() }
                    } catch (_: Throwable) {}
                }
            }
        }
    }
}
