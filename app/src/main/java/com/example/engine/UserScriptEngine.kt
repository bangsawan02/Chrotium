package com.example.engine

import android.webkit.WebView
import com.example.data.model.UserScript
import org.json.JSONObject

object UserScriptEngine {

    /**
     * Builds a JavaScript injection wrapper that provides Tampermonkey APIs (GM_addStyle, GM_setValue, GM_getValue, GM_log, etc.)
     * and executes the userscript inside a protected IIFE closure.
     */
    fun buildExecutableScript(script: UserScript): String {
        val scriptNameEscaped = JSONObject.quote(script.name)
        val rawCode = script.code

        return """
        (function() {
            try {
                const _SCRIPT_NAME = $scriptNameEscaped;
                const _BRIDGE = window.${TampermonkeyBridge.INTERFACE_NAME};

                // GM API Implementations
                function GM_addStyle(css) {
                    if (!css) return null;
                    const style = document.createElement('style');
                    style.setAttribute('data-userscript', _SCRIPT_NAME);
                    style.textContent = css;
                    (document.head || document.documentElement).appendChild(style);
                    return style;
                }

                function GM_setValue(key, value) {
                    if (_BRIDGE) {
                        const strVal = typeof value === 'object' ? JSON.stringify(value) : String(value);
                        _BRIDGE.GM_setValue(_SCRIPT_NAME + '_' + key, strVal);
                    }
                }

                function GM_getValue(key, defaultValue) {
                    if (_BRIDGE) {
                        const val = _BRIDGE.GM_getValue(_SCRIPT_NAME + '_' + key, defaultValue !== undefined ? String(defaultValue) : "");
                        return val || defaultValue;
                    }
                    return defaultValue;
                }

                function GM_deleteValue(key) {
                    if (_BRIDGE) {
                        _BRIDGE.GM_deleteValue(_SCRIPT_NAME + '_' + key);
                    }
                }

                function GM_log(...args) {
                    const msg = args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ');
                    if (_BRIDGE) {
                        _BRIDGE.GM_log(_SCRIPT_NAME, msg);
                    } else {
                        console.log('[' + _SCRIPT_NAME + ']', ...args);
                    }
                }

                function GM_notification(details, ondone) {
                    let text = typeof details === 'string' ? details : (details.text || details.title || "");
                    let title = typeof details === 'object' && details.title ? details.title : _SCRIPT_NAME;
                    if (_BRIDGE) {
                        _BRIDGE.GM_notification(text, title);
                    }
                }

                function GM_setClipboard(data, type) {
                    if (navigator.clipboard && navigator.clipboard.writeText) {
                        navigator.clipboard.writeText(String(data));
                    }
                }

                // Execute UserScript body
                $rawCode
                
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
        onScriptInjected: ((UserScript) -> Unit)? = null
    ): List<String> {
        if (url.isBlank() || url.startsWith("about:")) return emptyList()

        val matchingScripts = allScripts.filter { script ->
            if (!script.isEnabled || !script.matchesUrl(url)) return@filter false
            when (stage) {
                "document-start" -> script.runAt.equals("document-start", ignoreCase = true)
                "document-end" -> script.runAt.equals("document-end", ignoreCase = true) || script.runAt.isBlank()
                "document-idle" -> script.runAt.equals("document-idle", ignoreCase = true)
                else -> script.runAt.equals(stage, ignoreCase = true)
            }
        }

        val injectedNames = mutableListOf<String>()

        for (script in matchingScripts) {
            val executable = buildExecutableScript(script)
            webView.evaluateJavascript(executable, null)
            injectedNames.add(script.name)
            onScriptInjected?.invoke(script)
        }

        return injectedNames
    }
}
