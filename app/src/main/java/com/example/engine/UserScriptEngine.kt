package com.example.engine

import android.webkit.WebView
import com.example.data.model.UserScript

object UserScriptEngine {

    /**
     * Escape string safely for embedding as a JavaScript string literal.
     * Pure Kotlin implementation with zero Android SDK stub dependency.
     */
    fun escapeJsString(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20 || c.code in 0x7F..0x9F) {
                        sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    /**
     * Builds a JavaScript injection wrapper that provides standard Tampermonkey/Greasemonkey APIs
     * (unsafeWindow, GM_info, GM_addStyle, GM_setValue, GM_getValue, GM_xmlhttpRequest, GM object, etc.)
     * and executes the userscript inside a protected IIFE closure.
     */
    fun buildExecutableScript(script: UserScript, tabId: String = "tab_main"): String {
        val scriptNameEscaped = escapeJsString(script.name)
        val scriptDescEscaped = escapeJsString(script.description)
        val scriptAuthorEscaped = escapeJsString(script.author)
        val scriptVersionEscaped = escapeJsString(script.version)
        val scriptRunAtEscaped = escapeJsString(script.runAt)
        val scriptMatchesEscaped = escapeJsString(script.matchPatterns)
        val tabIdEscaped = escapeJsString(tabId)
        val rawCode = script.code

        return """
        (function() {
            try {
                const _SCRIPT_NAME = $scriptNameEscaped;
                const _TAB_ID = $tabIdEscaped;
                const _BRIDGE = window.${TampermonkeyBridge.INTERFACE_NAME};
                const unsafeWindow = typeof window !== 'undefined' ? window : this;

                // Tampermonkey / Greasemonkey Metadata Info Object
                const GM_info = {
                    script: {
                        name: $scriptNameEscaped,
                        version: $scriptVersionEscaped,
                        description: $scriptDescEscaped,
                        author: $scriptAuthorEscaped,
                        runAt: $scriptRunAtEscaped,
                        matches: $scriptMatchesEscaped
                    },
                    scriptHandler: "crotium Tampermonkey",
                    version: "1.0",
                    isIncognito: false
                };

                // GM API Implementations
                function GM_addStyle(css) {
                    if (!css) return null;
                    const style = document.createElement('style');
                    style.setAttribute('data-userscript', _SCRIPT_NAME);
                    style.textContent = css;
                    const target = document.head || document.documentElement || document.body;
                    if (target) {
                        target.appendChild(style);
                    } else {
                        document.addEventListener('DOMContentLoaded', () => {
                            (document.head || document.documentElement || document.body).appendChild(style);
                        });
                    }
                    return style;
                }

                function GM_setValue(key, value) {
                    if (_BRIDGE && _BRIDGE.GM_setValue) {
                        const payload = JSON.stringify({ type: typeof value, val: value });
                        _BRIDGE.GM_setValue(_SCRIPT_NAME, key, payload);
                    }
                }

                function GM_getValue(key, defaultValue) {
                    if (_BRIDGE && _BRIDGE.GM_getValue) {
                        const raw = _BRIDGE.GM_getValue(_SCRIPT_NAME, key, "__GM_NOT_FOUND__");
                        if (raw === "__GM_NOT_FOUND__" || raw === null || raw === undefined) {
                            return defaultValue;
                        }
                        try {
                            const parsed = JSON.parse(raw);
                            if (parsed && typeof parsed === 'object' && 'val' in parsed) {
                                return parsed.val;
                            }
                            return parsed;
                        } catch (e) {
                            return raw;
                        }
                    }
                    return defaultValue;
                }

                function GM_deleteValue(key) {
                    if (_BRIDGE && _BRIDGE.GM_deleteValue) {
                        _BRIDGE.GM_deleteValue(_SCRIPT_NAME, key);
                    }
                }

                function GM_listValues() {
                    if (_BRIDGE && _BRIDGE.GM_listValues) {
                        try {
                            const raw = _BRIDGE.GM_listValues(_SCRIPT_NAME);
                            return JSON.parse(raw);
                        } catch (e) {
                            return [];
                        }
                    }
                    return [];
                }

                function GM_log(...args) {
                    const msg = args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ');
                    if (_BRIDGE && _BRIDGE.GM_log) {
                        _BRIDGE.GM_log(_SCRIPT_NAME, msg);
                    } else {
                        console.log('[' + _SCRIPT_NAME + ']', ...args);
                    }
                }

                function GM_notification(details, ondone) {
                    let text = typeof details === 'string' ? details : (details && (details.text || details.title || ""));
                    let title = typeof details === 'object' && details && details.title ? details.title : _SCRIPT_NAME;
                    if (_BRIDGE && _BRIDGE.GM_notification) {
                        _BRIDGE.GM_notification(String(text), String(title));
                    }
                    if (typeof ondone === 'function') ondone();
                }

                function GM_setClipboard(data, type) {
                    try {
                        if (navigator.clipboard && navigator.clipboard.writeText) {
                            navigator.clipboard.writeText(String(data));
                        } else {
                            const el = document.createElement('textarea');
                            el.value = String(data);
                            document.body.appendChild(el);
                            el.select();
                            document.execCommand('copy');
                            document.body.removeChild(el);
                        }
                    } catch (e) {
                        console.warn('GM_setClipboard failed', e);
                    }
                }

                function GM_openInTab(url, options) {
                    return window.open(url, '_blank');
                }

                function GM_registerMenuCommand(name, fn) {
                    return name;
                }

                function GM_unregisterMenuCommand(id) {}

                function GM_getResourceText(name) { return ""; }
                function GM_getResourceURL(name) { return ""; }
                function GM_download(details) {}

                function GM_xmlhttpRequest(details) {
                    if (!details || !details.url) return;
                    if (!_BRIDGE || !_BRIDGE.GM_xmlhttpRequest_proxy) {
                        console.warn('GM_xmlhttpRequest_proxy not available');
                        return;
                    }

                    if (!window._gm_xhr_callbacks) window._gm_xhr_callbacks = {};
                    const requestId = Math.random().toString(36).substring(7);
                    
                    window._gm_xhr_callbacks[requestId] = function(response) {
                        if (response.status >= 200 && response.status < 400) {
                            if (typeof details.onload === 'function') details.onload(response);
                        } else {
                            if (typeof details.onerror === 'function') details.onerror(response);
                        }
                        if (typeof details.onloadend === 'function') details.onloadend(response);
                    };

                    const payload = JSON.stringify({
                        requestId: requestId,
                        url: details.url,
                        method: details.method || 'GET',
                        headers: details.headers || {},
                        data: details.data || ""
                    });

                    _BRIDGE.GM_xmlhttpRequest_proxy(payload, _SCRIPT_NAME, _TAB_ID);
                    
                    return { abort: () => {} };
                }

                // Greasemonkey v4 modern GM Promise wrapper
                const GM = {
                    info: GM_info,
                    addStyle: (css) => Promise.resolve(GM_addStyle(css)),
                    setValue: (k, v) => Promise.resolve(GM_setValue(k, v)),
                    getValue: (k, d) => Promise.resolve(GM_getValue(k, d)),
                    deleteValue: (k) => Promise.resolve(GM_deleteValue(k)),
                    listValues: () => Promise.resolve(GM_listValues()),
                    xmlHttpRequest: GM_xmlhttpRequest,
                    notification: (d, cb) => Promise.resolve(GM_notification(d, cb)),
                    setClipboard: (d, t) => Promise.resolve(GM_setClipboard(d, t)),
                    openInTab: (u, o) => Promise.resolve(GM_openInTab(u, o))
                };

                // Expose GM APIs to unsafeWindow / window scope for legacy scripts
                try {
                    unsafeWindow.GM_info = GM_info;
                    unsafeWindow.GM_addStyle = GM_addStyle;
                    unsafeWindow.GM_setValue = GM_setValue;
                    unsafeWindow.GM_getValue = GM_getValue;
                    unsafeWindow.GM_deleteValue = GM_deleteValue;
                    unsafeWindow.GM_listValues = GM_listValues;
                    unsafeWindow.GM_log = GM_log;
                    unsafeWindow.GM_notification = GM_notification;
                    unsafeWindow.GM_setClipboard = GM_setClipboard;
                    unsafeWindow.GM_openInTab = GM_openInTab;
                    unsafeWindow.GM_xmlhttpRequest = GM_xmlhttpRequest;
                    unsafeWindow.GM = GM;
                    unsafeWindow.unsafeWindow = unsafeWindow;
                } catch(e) {}

                // Execute UserScript body inside its isolated scope
                $rawCode
                ;

                if (_BRIDGE) {
                    _BRIDGE.logConsole('SUCCESS', _SCRIPT_NAME, 'Executed successfully');
                }
            } catch (err) {
                if (window.${TampermonkeyBridge.INTERFACE_NAME}) {
                    window.${TampermonkeyBridge.INTERFACE_NAME}.logConsole('ERROR', $scriptNameEscaped, (err && err.message) ? err.message : String(err));
                }
                console.error('Error running userscript ' + $scriptNameEscaped, err);
            }
        })();
        """.trimIndent()
    }

    /**
     * Inject applicable scripts into the WebView for the current stage (document-start, document-end, document-idle)
     * Returns list of script names that matched and were injected.
     */
    fun injectScriptsForStage(
        webView: WebView,
        url: String,
        stage: String,
        allScripts: List<UserScript>,
        tabId: String,
        onScriptInjected: ((UserScript) -> Unit)? = null
    ): List<String> {
        if (url.isBlank() || url.startsWith("about:")) return emptyList()

        val matchingScripts = allScripts.filter { script ->
            if (!script.isEnabled || !script.matchesUrl(url)) return@filter false
            when (stage) {
                "document-start" -> script.runAt.equals("document-start", ignoreCase = true)
                "document-end" -> script.runAt.equals("document-end", ignoreCase = true) || script.runAt.equals("document-body", ignoreCase = true) || script.runAt.isBlank()
                "document-idle" -> script.runAt.equals("document-idle", ignoreCase = true)
                else -> script.runAt.equals(stage, ignoreCase = true)
            }
        }

        val injectedNames = mutableListOf<String>()

        for (script in matchingScripts) {
            // Pre-validate JS syntax using QuickJS engine
            if (!QuickJsScriptEngine.validateJsSyntax(script.code)) {
                android.util.Log.w("UserScriptEngine", "Syntax warning for script: ${script.name}")
            }
            val executable = buildExecutableScript(script, tabId)
            webView.evaluateJavascript(executable, null)
            injectedNames.add(script.name)
            onScriptInjected?.invoke(script)
        }

        return injectedNames
    }
}

