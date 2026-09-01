package com.example.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.head
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

object KtorNetworkEngine {

    @Volatile
    private var _client: HttpClient? = null

    val client: HttpClient
        get() {
            return _client ?: synchronized(this) {
                _client ?: HttpClient(CIO) {
                    expectSuccess = false
                    engine {
                        requestTimeout = 30_000
                        endpoint {
                            maxConnectionsPerRoute = 20
                            keepAliveTime = 5000 // 5 seconds connection keep-alive
                        }
                    }
                }.also { _client = it }
            }
        }

    fun close() {
        synchronized(this) {
            try {
                _client?.close()
            } catch (e: Exception) {
                // Ignore closing exceptions
            }
            _client = null
        }
    }

    /**
     * Fetch URL content as String via Ktor CIO Engine.
     */
    suspend fun getAsString(
        url: String, 
        userAgent: String = "Mozilla/5.0 (Linux; Android 14) ChrotiumBrowser/1.0",
        customHeaders: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.get(url) {
                headers {
                    append(HttpHeaders.UserAgent, userAgent)
                    customHeaders.forEach { (k, v) -> append(k, v) }
                }
            }
            response.bodyAsText()
        } catch (e: Exception) {
            "Ktor Engine Error: ${e.localizedMessage}"
        }
    }

    data class UrlHeaderInfo(
        val contentLength: Long,
        val supportsRanges: Boolean,
        val contentType: String,
        val contentDisposition: String?
    )

    /**
     * Query header info for file downloads.
     */
    suspend fun getHeaderInfo(url: String, userAgent: String, cookieHeader: String? = null): UrlHeaderInfo = withContext(Dispatchers.IO) {
        try {
            val response: HttpResponse = client.head(url) {
                headers {
                    append(HttpHeaders.UserAgent, userAgent)
                    if (!cookieHeader.isNullOrBlank()) {
                        append(HttpHeaders.Cookie, cookieHeader)
                    }
                }
            }
            val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
            val ranges = response.headers[HttpHeaders.AcceptRanges]?.lowercase() == "bytes" || 
                         response.headers["content-range"] != null
            val contentType = response.headers[HttpHeaders.ContentType] ?: "*/*"
            val disposition = response.headers[HttpHeaders.ContentDisposition]
            UrlHeaderInfo(length, ranges, contentType, disposition)
        } catch (e: Exception) {
            UrlHeaderInfo(-1L, false, "*/*", null)
        }
    }

    /**
     * Download a range chunk into a file at a specific offset.
     */
    suspend fun downloadRangeChunk(
        url: String,
        startByte: Long,
        endByte: Long,
        targetFile: File,
        userAgent: String,
        cookieHeader: String? = null,
        onProgress: (bytesRead: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            client.prepareGet(url) {
                headers {
                    append(HttpHeaders.UserAgent, userAgent)
                    if (!cookieHeader.isNullOrBlank()) {
                        append(HttpHeaders.Cookie, cookieHeader)
                    }
                    if (endByte > 0) {
                        append(HttpHeaders.Range, "bytes=$startByte-$endByte")
                    } else if (startByte > 0) {
                        append(HttpHeaders.Range, "bytes=$startByte-")
                    }
                }
            }.execute { response ->
                if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.PartialContent) {
                    return@execute false
                }

                val channel: ByteReadChannel = response.bodyAsChannel()
                val raf = RandomAccessFile(targetFile, "rw")
                raf.seek(startByte)

                val buffer = ByteArray(8192)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    raf.write(buffer, 0, read)
                    onProgress(read)
                }
                raf.close()
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
