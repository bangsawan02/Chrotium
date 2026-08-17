package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.regex.Pattern

data class ParsedScriptMetadata(
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val runAt: String,
    val matchPatterns: String
)

@Entity(tableName = "userscripts")
data class UserScript(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val author: String = "Custom",
    val version: String = "1.0",
    val matchPatterns: String = "*://*/*",
    val runAt: String = "document-idle",
    val code: String,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val executionCount: Int = 0,
    val lastExecutedTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun matchesUrl(targetUrl: String): Boolean {
        if (!isEnabled || targetUrl.isBlank() || targetUrl.startsWith("about:")) {
            return false
        }

        val patterns = matchPatterns.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (patterns.isEmpty()) return true

        for (pattern in patterns) {
            if (pattern == "*" || pattern == "*://*/*" || pattern == "<all_urls>") {
                return true
            }
            if (matchWildcard(pattern, targetUrl)) {
                return true
            }
        }
        return false
    }

    companion object {
        private val patternCache = java.util.concurrent.ConcurrentHashMap<String, Pattern>()

        fun matchWildcard(pattern: String, url: String): Boolean {
            return try {
                if (pattern == url) return true

                val compiled = patternCache.getOrPut(pattern) {
                    var regexStr = pattern
                        .replace(".", "\\.")
                        .replace("?", "\\?")
                        .replace("*://", "(http://|https://)")
                        .replace("*", ".*")

                    if (!regexStr.startsWith(".*") && !regexStr.startsWith("(") && !regexStr.startsWith("http")) {
                        regexStr = ".*$regexStr"
                    }
                    if (!regexStr.endsWith(".*")) {
                        regexStr = "$regexStr.*"
                    }

                    Pattern.compile("^$regexStr$", Pattern.CASE_INSENSITIVE)
                }
                compiled.matcher(url).find()
            } catch (e: Exception) {
                false
            }
        }

        fun parseMetadata(code: String): ParsedScriptMetadata {
            var name = "Untitled Script"
            var description = ""
            var author = "Tampermonkey User"
            var version = "1.0"
            var runAt = "document-idle"
            val matches = mutableListOf<String>()

            val lines = code.lines()
            var inHeader = false

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.contains("==UserScript==")) {
                    inHeader = true
                    continue
                }
                if (trimmed.contains("==/UserScript==")) {
                    break
                }
                if (inHeader && trimmed.startsWith("//")) {
                    val content = trimmed.removePrefix("//").trim()
                    when {
                        content.startsWith("@name", ignoreCase = true) -> {
                            name = content.substringAfter("@name").trim()
                        }
                        content.startsWith("@description", ignoreCase = true) -> {
                            description = content.substringAfter("@description").trim()
                        }
                        content.startsWith("@author", ignoreCase = true) -> {
                            author = content.substringAfter("@author").trim()
                        }
                        content.startsWith("@version", ignoreCase = true) -> {
                            version = content.substringAfter("@version").trim()
                        }
                        content.startsWith("@run-at", ignoreCase = true) -> {
                            val rawRunAt = content.substringAfter("@run-at").trim().lowercase()
                            runAt = when {
                                rawRunAt.contains("document-start") -> "document-start"
                                rawRunAt.contains("document-end") -> "document-end"
                                else -> "document-idle"
                            }
                        }
                        content.startsWith("@match", ignoreCase = true) -> {
                            val matchVal = content.substringAfter("@match").trim()
                            if (matchVal.isNotEmpty()) matches.add(matchVal)
                        }
                        content.startsWith("@include", ignoreCase = true) -> {
                            val includeVal = content.substringAfter("@include").trim()
                            if (includeVal.isNotEmpty()) matches.add(includeVal)
                        }
                    }
                }
            }

            val patternString = if (matches.isEmpty()) "*://*/*" else matches.joinToString(", ")

            return ParsedScriptMetadata(
                name = name,
                description = description,
                author = author,
                version = version,
                runAt = runAt,
                matchPatterns = patternString
            )
        }
    }
}
