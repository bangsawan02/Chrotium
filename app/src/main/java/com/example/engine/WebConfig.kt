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
     * tidak pernah menjeda video ketika aplikasi diminimalkan atau layar mati.
     */
    val BACKGROUND_PLAY_SCRIPT = """
        (function() {
            if (window.__chrotium_bg_play_injected) return;
            window.__chrotium_bg_play_injected = true;

            try {
                // 1. Force Page Visibility API to always report visible
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

                // 2. Intercept visibilitychange & pagehide listeners
                var origAddEventListener = EventTarget.prototype.addEventListener;
                EventTarget.prototype.addEventListener = function(type, listener, options) {
                    if (type === 'visibilitychange' || type === 'webkitvisibilitychange' || type === 'pagehide' || type === 'blur') {
                        var wrappedListener = function(e) {
                            if (e && (e.type === 'visibilitychange' || e.type === 'webkitvisibilitychange')) {
                                return; // Block event dispatch
                            }
                            if (listener) listener.apply(this, arguments);
                        };
                        return origAddEventListener.call(this, type, wrappedListener, options);
                    }
                    return origAddEventListener.call(this, type, listener, options);
                };

                // 3. Block window blur auto-pause
                window.addEventListener('blur', function(e) {
                    if (e) e.stopImmediatePropagation();
                }, true);

                // 4. Prevent HTMLVideoElement from auto-pausing when backgrounded
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
                        transform: translateZ(0) !important;
                        will-change: transform, opacity !important;
                        backface-visibility: hidden !important;
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
                        transform: translateZ(0) !important;
                        will-change: transform !important;
                        backface-visibility: hidden !important;
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
