package com.example.engine

import com.example.data.db.AppDatabase
import com.example.data.model.SuggestionItem
import com.example.data.model.SuggestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SuggestionEngine(private val database: AppDatabase) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private val commonDomains = listOf(
        "google.com", "youtube.com", "github.com", "wikipedia.org", "reddit.com",
        "twitter.com", "x.com", "instagram.com", "facebook.com", "duckduckgo.com",
        "medium.com", "stackoverflow.com", "amazon.com", "netflix.com", "linkedin.com",
        "detik.com", "kompas.com", "tokopedia.com", "shopee.co.id", "tribunnews.com"
    )

    suspend fun getSuggestions(query: String): List<SuggestionItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || trimmed.startsWith("about:")) {
            return@withContext emptyList()
        }

        val results = mutableListOf<SuggestionItem>()

        // 1. Direct Web Navigation Match if query looks like a domain / starts with letters
        val matchedDomains = commonDomains.filter { it.contains(trimmed, ignoreCase = true) }.take(2)
        for (domain in matchedDomains) {
            results.add(
                SuggestionItem(
                    title = domain,
                    destinationUrl = "https://$domain",
                    type = SuggestionType.DIRECT_URL,
                    subtitle = "Buka Langsung"
                )
            )
        }

        // 2. Local Bookmarks Match
        try {
            val bookmarks = database.bookmarkDao().searchBookmarks(trimmed)
            for (bm in bookmarks.take(3)) {
                if (results.none { it.destinationUrl.equals(bm.url, ignoreCase = true) }) {
                    results.add(
                        SuggestionItem(
                            title = bm.title.ifBlank { bm.url },
                            destinationUrl = bm.url,
                            type = SuggestionType.BOOKMARK,
                            subtitle = "Penanda Halaman"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // safe ignore
        }

        // 3. Local History Match
        try {
            val historyItems = database.historyDao().searchHistory(trimmed)
            for (h in historyItems.take(3)) {
                if (results.none { it.destinationUrl.equals(h.url, ignoreCase = true) }) {
                    results.add(
                        SuggestionItem(
                            title = h.title.ifBlank { h.url },
                            destinationUrl = h.url,
                            type = SuggestionType.HISTORY,
                            subtitle = "Riwayat Kunjungan"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // safe ignore
        }

        // 4. Real-time Search Query Autocomplete API (DuckDuckGo / Google Complete)
        try {
            val apiSuggestions = fetchSearchQuerySuggestions(trimmed)
            for (suggestion in apiSuggestions) {
                if (results.none { it.title.equals(suggestion, ignoreCase = true) }) {
                    results.add(
                        SuggestionItem(
                            title = suggestion,
                            destinationUrl = suggestion,
                            type = SuggestionType.QUERY,
                            subtitle = "Pencarian Web"
                        )
                    )
                }
                if (results.size >= 8) break
            }
        } catch (e: Exception) {
            // safe ignore offline
        }

        results.take(8)
    }

    private fun fetchSearchQuerySuggestions(query: String): List<String> {
        val list = mutableListOf<String>()
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            return list
        }

        // 1. Coba DuckDuckGo Autocomplete API via OkHttp
        try {
            val request = Request.Builder()
                .url("https://duckduckgo.com/ac/?q=$encodedQuery&type=list")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: ""
                    if (responseText.startsWith("[")) {
                        val jsonArray = JSONArray(responseText)
                        if (jsonArray.length() > 1) {
                            val suggestionsArray = jsonArray.getJSONArray(1)
                            for (i in 0 until suggestionsArray.length().coerceAtMost(6)) {
                                list.add(suggestionsArray.getString(i))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Lanjutkan ke fallback
        }

        if (list.isNotEmpty()) return list

        // 2. Fallback ke Google Suggestions jika DuckDuckGo kosong via OkHttp
        try {
            val request = Request.Builder()
                .url("https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: ""
                    val jsonArray = JSONArray(responseText)
                    if (jsonArray.length() > 1) {
                        val suggestionsArray = jsonArray.getJSONArray(1)
                        for (i in 0 until suggestionsArray.length().coerceAtMost(6)) {
                            list.add(suggestionsArray.getString(i))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Abaikan kesalahan fallback
        }

        return list
    }

    /**
     * Closes the OkHttpClient connection pool and executor service to prevent memory and socket leaks.
     */
    fun clearCache() {
        try {
            okHttpClient.connectionPool.evictAll()
        } catch (e: Exception) {
            // safe ignore
        }
    }

    fun close() {
        try {
            okHttpClient.dispatcher.executorService.shutdown()
            okHttpClient.connectionPool.evictAll()
        } catch (e: Exception) {
            // safe ignore
        }
    }
}
