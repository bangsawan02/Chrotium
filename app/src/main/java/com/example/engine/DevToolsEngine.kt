package com.example.engine

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Built-in Chrome DevTools Engine berbasis Chrome DevTools Protocol (CDP).
 * Menghubungkan WebView DevTools Frontend (layar inspeksi) ke WebView Utama secara native
 * melalui jembatan Local Unix Socket (`webview_devtools_remote_<PID>`) dan TCP Localhost.
 * Mendukung mode Offline Built-in Inspector dan Online Official Google DevTools Frontend (appspot.com).
 */
object DevToolsEngine {

    private const val TAG = "DevToolsEngine"
    private const val DEFAULT_PORT = 9222

    // Daftar Hash Commit Chromium Resmi Stabil untuk Google DevTools Frontend
    val STABLE_CHROMIUM_HASHES = listOf(
        "d0f9461159b3bc58b292350811b7b15a6b0c2eb7" to "Chromium 120+ (Rekomendasi)",
        "18625902047ca88456209b1104e74fb9c9bf66b3" to "Chromium 114 (Stable)",
        "820eb65e8658d112b91c770d4d3d4e41ce9ef7a0" to "Chromium 109 (LTS)",
        "08539fb04918e9ebc7fa25ab05c48b29b626efb8" to "Chromium 86 (Classic)"
    )

    const val DEFAULT_CHROMIUM_HASH = "d0f9461159b3bc58b292350811b7b15a6b0c2eb7"

    @Volatile
    var selectedCommitHash: String = DEFAULT_CHROMIUM_HASH

    @Volatile
    var serverPort: Int = DEFAULT_PORT
        private set

    private var serverSocket: ServerSocket? = null
    private var bridgeJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO)

    data class DevToolsTarget(
        val id: String,
        val title: String,
        val url: String,
        val webSocketDebuggerUrl: String?,
        val devtoolsFrontendUrl: String?
    )

    /**
     * Mengaktifkan WebContentsDebugging di level Chromium Android
     */
    fun enableWebViewDebugging(webView: WebView) {
        try {
            WebView.setWebContentsDebuggingEnabled(true)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengaktifkan WebContentsDebugging", e)
        }
    }

    /**
     * Memulai TCP-to-UnixSocket CDP Bridge Server di localhost.
     * Mengembalikan port TCP yang berhasil di-bind.
     */
    @Synchronized
    fun startCdpBridgeServer(): Int {
        if (isRunning.get() && serverSocket != null && !serverSocket!!.isClosed) {
            return serverPort
        }

        try {
            // Coba bind ke port default 9222 atau cari port bebas
            var socket: ServerSocket? = null
            for (p in DEFAULT_PORT..9250) {
                try {
                    socket = ServerSocket(p, 50, InetAddress.getByName("127.0.0.1"))
                    serverPort = p
                    break
                } catch (e: Exception) {
                    // Coba port berikutnya jika sibuk
                }
            }

            if (socket == null) {
                socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
                serverPort = socket.localPort
            }

            serverSocket = socket
            isRunning.set(true)
            Log.i(TAG, "CDP Bridge Server listening on 127.0.0.1:$serverPort")

            bridgeJob = scope.launch {
                acceptConnections(socket)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai CDP Bridge Server", e)
        }

        return serverPort
    }

    /**
     * Loop utama menerima koneksi dari WebView DevTools Frontend (WebSocket / HTTP).
     */
    private suspend fun acceptConnections(server: ServerSocket) {
        val pid = Process.myPid()
        val socketName = "webview_devtools_remote_$pid"

        while (scope.isActive && isRunning.get()) {
            try {
                val clientSocket = server.accept()
                scope.launch(Dispatchers.IO) {
                    handleClientConnection(clientSocket, socketName)
                }
            } catch (e: Exception) {
                if (!isRunning.get()) break
                Log.w(TAG, "Error accepting client connection: ${e.message}")
            }
        }
    }

    /**
     * Meneruskan stream byte secara dua arah antara client TCP dan Unix Domain Socket Chromium.
     * Membersihkan / memodifikasi header 'Origin' pada handshake WebSocket agar tidak ditolak oleh Chromium DevTools.
     */
    private fun handleClientConnection(clientSocket: Socket, unixSocketName: String) {
        var unixSocket: LocalSocket? = null
        try {
            unixSocket = LocalSocket()
            unixSocket.connect(
                LocalSocketAddress(unixSocketName, LocalSocketAddress.Namespace.ABSTRACT)
            )

            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()
            val unixIn = unixSocket.inputStream
            val unixOut = unixSocket.outputStream

            // Forward Client -> Chromium Unix Socket (dengan pembersihan Origin header pada handshake)
            val clientToUnixJob = scope.launch(Dispatchers.IO) {
                pipeClientToUnix(clientIn, unixOut)
            }

            // Forward Chromium Unix Socket -> Client
            val unixToClientJob = scope.launch(Dispatchers.IO) {
                pipeStreams(unixIn, clientOut)
            }

            // Tunggu hingga salah satu stream selesai
            scope.launch(Dispatchers.IO) {
                clientToUnixJob.join()
                unixToClientJob.join()
                safeClose(clientSocket, unixSocket)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menghubungkan ke unix socket $unixSocketName: ${e.message}")
            safeClose(clientSocket, unixSocket)
        }
    }

    /**
     * Membaca header HTTP awal dari client, mengganti atau menghapus 'Origin: null' atau origin eksternal
     * menjadi 'Origin: http://127.0.0.1', lalu meneruskan payload/WebSocket frame selanjutnya secara transparan.
     */
    private fun pipeClientToUnix(input: InputStream, output: OutputStream) {
        try {
            val headerBuffer = java.io.ByteArrayOutputStream()
            val temp = ByteArray(1024)
            var foundHeaderEnd = false
            var extraBytes: ByteArray? = null

            // Baca sampai menemukan pemisah header HTTP "\r\n\r\n"
            while (!foundHeaderEnd) {
                val read = input.read(temp)
                if (read == -1) break

                val prevLen = headerBuffer.size()
                headerBuffer.write(temp, 0, read)
                val allBytes = headerBuffer.toByteArray()

                // Cari pattern \r\n\r\n
                for (i in 0 until allBytes.size - 3) {
                    if (allBytes[i] == '\r'.code.toByte() &&
                        allBytes[i + 1] == '\n'.code.toByte() &&
                        allBytes[i + 2] == '\r'.code.toByte() &&
                        allBytes[i + 3] == '\n'.code.toByte()
                    ) {
                        foundHeaderEnd = true
                        val headerBytes = allBytes.copyOfRange(0, i + 4)
                        if (i + 4 < allBytes.size) {
                            extraBytes = allBytes.copyOfRange(i + 4, allBytes.size)
                        }

                        // Parse dan modifikasi header HTTP
                        var headerStr = String(headerBytes, Charsets.UTF_8)
                        // Ubah Origin null atau appspot menjadi Origin lokal yang diizinkan Chromium
                        headerStr = headerStr.replace(Regex("(?i)Origin:\\s*[^\r\n]+"), "Origin: http://127.0.0.1:$serverPort")
                        
                        val modifiedHeaderBytes = headerStr.toByteArray(Charsets.UTF_8)
                        output.write(modifiedHeaderBytes)
                        if (extraBytes != null && extraBytes.isNotEmpty()) {
                            output.write(extraBytes)
                        }
                        output.flush()
                        break
                    }
                }

                // Jika sudah melebihi 32KB tanpa header end, bypass langsung
                if (headerBuffer.size() > 32768) {
                    output.write(headerBuffer.toByteArray())
                    output.flush()
                    break
                }
            }

            // Lanjutkan piping transparan untuk sisa stream (WebSocket data frames)
            pipeStreams(input, output)
        } catch (_: Exception) {
            // Stream selesai
        }
    }

    private fun pipeStreams(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                output.flush()
            }
        } catch (_: Exception) {
            // Stream ditutup secara normal
        }
    }

    private fun safeClose(clientSocket: Socket?, unixSocket: LocalSocket?) {
        try { clientSocket?.close() } catch (_: Exception) {}
        try { unixSocket?.close() } catch (_: Exception) {}
    }

    /**
     * Menghasilkan URL DevTools Frontend resmi dari Google App Engine (chrome-devtools-frontend.appspot.com)
     */
    fun getDevToolsUrl(
        port: Int = serverPort,
        pageId: String? = null,
        commitHash: String = selectedCommitHash
    ): String {
        val targetPage = pageId ?: "0"
        return "https://chrome-devtools-frontend.appspot.com/serve_file/@$commitHash/inspector.html?ws=127.0.0.1:$port/devtools/page/$targetPage"
    }

    /**
     * Mengambil daftar target page yang aktif dari endpoint CDP /json
     */
    suspend fun fetchActivePageTarget(port: Int = serverPort): DevToolsTarget? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("http://127.0.0.1:$port/json")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 2000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(jsonStr)
                if (jsonArray.length() > 0) {
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val type = obj.optString("type", "page")
                        if (type == "page") {
                            return@withContext DevToolsTarget(
                                id = obj.optString("id"),
                                title = obj.optString("title"),
                                url = obj.optString("url"),
                                webSocketDebuggerUrl = obj.optString("webSocketDebuggerUrl"),
                                devtoolsFrontendUrl = obj.optString("devtoolsFrontendUrl")
                            )
                        }
                    }
                    val first = jsonArray.getJSONObject(0)
                    return@withContext DevToolsTarget(
                        id = first.optString("id"),
                        title = first.optString("title"),
                        url = first.optString("url"),
                        webSocketDebuggerUrl = first.optString("webSocketDebuggerUrl"),
                        devtoolsFrontendUrl = first.optString("devtoolsFrontendUrl")
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal query /json target: ${e.message}")
        }
        null
    }

    /**
     * Menghentikan bridge server saat tidak digunakan.
     */
    @Synchronized
    fun stopCdpBridgeServer() {
        isRunning.set(false)
        try {
            bridgeJob?.cancel()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket: ${e.message}")
        }
        serverSocket = null
    }
}
