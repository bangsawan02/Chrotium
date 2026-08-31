package com.example.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KtorNetworkEngine {

    val client: HttpClient by lazy {
        HttpClient(CIO) {
            expectSuccess = false
            engine {
                requestTimeout = 10_000
            }
        }
    }

    /**
     * High-performance asynchronous HTTP GET request powered by Ktor CIO Coroutine Engine.
     */
    suspend fun getAsString(url: String, userAgent: String = "Mozilla/5.0 (Linux; Android 14) ChrotiumBrowser/1.0"): String = withContext(Dispatchers.IO) {
        try {
            val response = client.get(url) {
                headers {
                    append("User-Agent", userAgent)
                }
            }
            response.bodyAsText()
        } catch (e: Exception) {
            "Ktor Engine Error: ${e.localizedMessage}"
        }
    }
}
