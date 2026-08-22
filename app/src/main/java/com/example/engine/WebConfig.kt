package com.example.engine

import android.os.Build

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

                // 2. Enforce navigator.userAgentData mobile flag
                if (navigator.userAgentData) {
                    try {
                        Object.defineProperty(navigator, 'userAgentData', {
                            get: function() {
                                return {
                                    mobile: true,
                                    platform: 'Android',
                                    brands: [
                                        { brand: 'Google Chrome', version: '130' },
                                        { brand: 'Chromium', version: '130' },
                                        { brand: 'Android WebView', version: '130' }
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
                                        { brand: 'Google Chrome', version: '130' },
                                        { brand: 'Chromium', version: '130' },
                                        { brand: 'Not?A_Brand', version: '24' }
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
     * Memalsukan Page Visibility API agar YouTube dan situs web video lainnya
     * tidak pernah menjeda video ketika aplikasi diminimalkan atau layar mati,
     * dengan tetap menjaga kompatibilitas penuh dengan Zone.js / Angular dan React.
     */
    val BACKGROUND_PLAY_SCRIPT = """
        (function() {
            if (window.__chrotium_bg_play_injected) return;
            window.__chrotium_bg_play_injected = true;

            try {
                // 1. Force Page Visibility API to always report visible
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
                } catch(e) {}

                // 2. Intercept visibilitychange & blur using capture phase (safe for Zone.js / Angular)
                document.addEventListener('visibilitychange', function(e) {
                    if (e) e.stopImmediatePropagation();
                }, true);
                document.addEventListener('webkitvisibilitychange', function(e) {
                    if (e) e.stopImmediatePropagation();
                }, true);
                window.addEventListener('pagehide', function(e) {
                    if (e) e.stopImmediatePropagation();
                }, true);
                window.addEventListener('blur', function(e) {
                    if (e) e.stopImmediatePropagation();
                }, true);

                // 3. Prevent HTMLVideoElement from auto-pausing when backgrounded
                var origPause = HTMLVideoElement.prototype.pause;
                HTMLVideoElement.prototype.pause = function() {
                    if (document.hidden) {
                        return; // Ignore auto pause request during hidden state
                    }
                    return origPause.apply(this, arguments);
                };
            } catch(e) {
                console.error('[Chrotium] Background Play injection error:', e);
            }
        })();
    """.trimIndent()

    /**
     * Script Optimasi Khusus untuk Framework Modern SPA: Angular (Zone.js, Router, CDK),
     * React, Vue, Svelte, dan Web Components.
     * - Mengoptimalkan render pipeline, eliminasi 300ms touch delay pada tombol Material/CDK.
     * - Menjamin kompatibilitas mutlak dengan Angular Zone.js Change Detection.
     * - Mempercepat komposit GPU untuk komponen app-root, router-outlet, dan CDK overlay.
     * - Memantau navigasi Single Page Application (SPA history pushState/replaceState).
     */
    val ANGULAR_SPA_OPTIMIZATION_SCRIPT = """
        (function() {
            if (window.__chrotium_angular_spa_injected) return;
            window.__chrotium_angular_spa_injected = true;

            try {
                // 1. Injeksi CSS hardware acceleration & touch-action untuk komponen Angular / SPA
                var style = document.createElement('style');
                style.id = 'chrotium-angular-spa-style';
                style.textContent = `
                    /* Akselerasi GPU untuk container utama Angular & SPA */
                    app-root, [ng-version], router-outlet + *, ng-component,
                    #root, #__next, #app, .app-container {
                        contain: layout style;
                        -webkit-overflow-scrolling: touch;
                    }

                    /* Optimasi Angular Material & CDK Overlay */
                    .cdk-overlay-container, .cdk-global-overlay-wrapper {
                        pointer-events: auto !important;
                        z-index: 1000 !important;
                    }

                    /* Eliminasi 300ms tap delay untuk interaktivitas tombol instan */
                    button, a, input, select, textarea, [role="button"],
                    [mat-button], [mat-icon-button], [mat-raised-button], [mat-flat-button],
                    .mat-mdc-button-base, mat-select, mat-checkbox, mat-radio-button, mat-slide-toggle {
                        touch-action: manipulation;
                        -webkit-tap-highlight-color: transparent;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);

                // 2. Optimasi SPA & YouTube Real-Time URL Tracking (Angular, React, Vue, YouTube, Twitter)
                var lastReportedUrl = location.href;
                var lastReportedTitle = document.title;

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
                        }
                    } catch(e) {}
                }

                function hookHistoryMethod(method) {
                    var original = history[method];
                    if (typeof original === 'function') {
                        history[method] = function() {
                            var result = original.apply(this, arguments);
                            setTimeout(reportUrlChange, 10);
                            try {
                                var ev = new CustomEvent('chrotium:spapathchange', {
                                    detail: { url: location.href, title: document.title }
                                });
                                window.dispatchEvent(ev);
                            } catch(e) {}
                            return result;
                        };
                    }
                }
                hookHistoryMethod('pushState');
                hookHistoryMethod('replaceState');

                window.addEventListener('popstate', function() { setTimeout(reportUrlChange, 10); }, true);
                window.addEventListener('hashchange', function() { setTimeout(reportUrlChange, 10); }, true);
                window.addEventListener('yt-navigate-finish', function() { setTimeout(reportUrlChange, 10); }, true);
                window.addEventListener('yt-page-data-updated', function() { setTimeout(reportUrlChange, 10); }, true);
                window.addEventListener('yt-action', function() { setTimeout(reportUrlChange, 25); }, true);

                // Polling ringan untuk menjamin URL selalu sinkron di SPA dinamis
                setInterval(reportUrlChange, 350);

                // 3. Konfigurasi Zone.js Performance Hints jika Angular digunakan
                if (typeof window !== 'undefined') {
                    window.__Zone_disable_on_property = false;
                }
            } catch(err) {
                console.error('[Chrotium] Angular & SPA optimization error:', err);
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
     * Script khusus optimasi YouTube untuk menghilangkan patah-patah/lag:
     * - Memaksa compositing GPU hardware acceleration pada elemen video.
     * - Menonaktifkan Ambient Mode / efek pencahayaan latar belakang YouTube yang membebankan rendering GPU.
     * - Mencegah frame drops dan memuluskan pemutaran video 60FPS.
     */
    val YOUTUBE_PERFORMANCE_SCRIPT = """
        (function() {
            if (window.__chrotium_yt_perf_injected) return;
            window.__chrotium_yt_perf_injected = true;

            try {
                // 1. Inject Hardware Acceleration CSS rules for YouTube video player
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
                    /* Disable heavy YouTube Ambient Mode / Cinematics effect causing GPU lag */
                    #cinematics, ytd-watch-flexy[ambient-mode] #cinematics, .ytp-gradient-bottom, .ytp-gradient-top {
                        display: none !important;
                        opacity: 0 !important;
                        visibility: hidden !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);

                // 2. Optimize YouTube Player performance configuration if present
                if (window.yt && window.yt.config_) {
                    window.yt.config_.EXPERIMENT_FLAGS = window.yt.config_.EXPERIMENT_FLAGS || {};
                    window.yt.config_.EXPERIMENT_FLAGS.web_enable_ab_testing = false;
                    window.yt.config_.EXPERIMENT_FLAGS.html5_enable_subframe_player = false;
                }
            } catch(e) {}
        })();
    """.trimIndent()

    /**
     * Script untuk mem-bypass dan menghilangkan jeda iklan YouTube (mengeliminasi 5 detik delay/timeout)
     * - Menghapus adPlacements, playerAds, dan adSlots dari response player YouTube agar tidak ada ad-fetching.
     * - Mempercepat pemutaran video tanpa menunggu timer ad-timeout.
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
                        if (requestURL.includes('/youtubei/v1/player')) {
                            const originalOnReadyStateChange = xhr.onreadystatechange;
                            xhr.onreadystatechange = function() {
                                if (xhr.readyState === 4 && xhr.status === 200) {
                                    try {
                                        const text = xhr.responseText;
                                        if (text && (text.includes('adPlacements') || text.includes('playerAds'))) {
                                            const json = JSON.parse(text);
                                            if (json) {
                                                delete json.adPlacements;
                                                delete json.playerAds;
                                                delete json.adSlots;
                                                // Override responseText via property descriptor
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
                        if (url.includes('/youtubei/v1/player')) {
                            const clone = response.clone();
                            const json = await clone.json();
                            if (json) {
                                delete json.adPlacements;
                                delete json.playerAds;
                                delete json.adSlots;
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

                // 3. Fast ad skipper & cleaner interval
                setInterval(function() {
                    try {
                        const adOverlay = document.querySelector('.ytp-ad-overlay-container, .ytp-ad-module');
                        if (adOverlay) {
                            adOverlay.remove();
                        }
                        const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern');
                        if (skipBtn) {
                            skipBtn.click();
                        }
                        const video = document.querySelector('video');
                        if (video && document.querySelector('.ad-showing')) {
                            video.currentTime = video.duration || video.currentTime + 10;
                        }
                    } catch(e) {}
                    
                    if (window.ytInitialPlayerResponse) {
                        delete window.ytInitialPlayerResponse.adPlacements;
                        delete window.ytInitialPlayerResponse.playerAds;
                        delete window.ytInitialPlayerResponse.adSlots;
                    }
                }, 100);

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
     * Script untuk memastikan elemen input teks / form selalu terlihat jelas diatas keyboard
     * ketika pengguna melakukan tap pada input box situs web.
     */
    val INPUT_SCROLL_FOCUS_SCRIPT = """
        (function() {
            if (window.__chrotium_input_scroll_injected) return;
            window.__chrotium_input_scroll_injected = true;

            function scrollActiveIntoView(target) {
                var el = target || document.activeElement;
                if (el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable)) {
                    setTimeout(function() {
                        try {
                            el.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'nearest' });
                        } catch(e) {
                            try { el.scrollIntoViewIfNeeded(); } catch(e2) {}
                        }
                    }, 50);
                }
            }

            document.addEventListener('focusin', function(e) {
                scrollActiveIntoView(e.target);
            }, true);

            window.addEventListener('resize', function() {
                scrollActiveIntoView();
            });
        })();
    """.trimIndent()
}
