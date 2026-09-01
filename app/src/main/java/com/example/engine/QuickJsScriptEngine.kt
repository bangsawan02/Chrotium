package com.example.engine

import app.cash.quickjs.QuickJs

object QuickJsScriptEngine {

    /**
     * Executes standalone JavaScript using the CashApp QuickJS Engine on a background thread.
     */
    fun evaluate(code: String): String {
        return try {
            QuickJs.create().use { quickJs ->
                val result = quickJs.evaluate(code)
                result?.toString() ?: "undefined"
            }
        } catch (e: Exception) {
            "QuickJS Execution Error: ${e.localizedMessage}"
        }
    }

    /**
     * Validates JavaScript syntax before injecting into WebView or UserScripts.
     */
    fun validateJsSyntax(code: String): Boolean {
        return try {
            QuickJs.create().use { quickJs ->
                quickJs.evaluate("try { (function(){ $code })(); true; } catch(e) { false; }") as? Boolean ?: false
            }
        } catch (e: Exception) {
            false
        }
    }
}
