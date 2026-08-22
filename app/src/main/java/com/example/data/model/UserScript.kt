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

        val allRules = matchPatterns.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (allRules.isEmpty()) return true

        val includeRules = mutableListOf<String>()
        val excludeRules = mutableListOf<String>()

        for (rule in allRules) {
            if (rule.startsWith("!")) {
                excludeRules.add(rule.removePrefix("!").trim())
            } else {
                includeRules.add(rule)
            }
        }

        // Check if URL is explicitly excluded
        for (exclude in excludeRules) {
            if (matchPattern(exclude, targetUrl)) {
                return false
            }
        }

        if (includeRules.isEmpty()) return true

        for (pattern in includeRules) {
            if (pattern == "*" || pattern == "*://*/*" || pattern == "<all_urls>") {
                return true
            }
            if (matchPattern(pattern, targetUrl)) {
                return true
            }
        }

        return false
    }

    companion object {
        private val patternCache = java.util.concurrent.ConcurrentHashMap<String, Pattern>()

        fun matchPattern(pattern: String, url: String): Boolean {
            if (pattern == "*" || pattern == "<all_urls>" || pattern == "*://*/*") return true
            if (pattern == url) return true

            return try {
                val compiled = patternCache.getOrPut(pattern) {
                    compilePatternToRegex(pattern)
                }
                compiled.matcher(url).find()
            } catch (e: Exception) {
                false
            }
        }

        fun matchWildcard(pattern: String, url: String): Boolean {
            return matchPattern(pattern, url)
        }

        private fun compilePatternToRegex(pattern: String): Pattern {
            val trimmed = pattern.trim()

            // 1. Check if pattern is a regular expression: /pattern/ or /pattern/i
            if (trimmed.startsWith("/") && trimmed.lastIndexOf("/") > 0) {
                val lastSlash = trimmed.lastIndexOf("/")
                val regexBody = trimmed.substring(1, lastSlash)
                val flags = trimmed.substring(lastSlash + 1)
                val isCaseInsensitive = flags.contains("i", ignoreCase = true)
                return Pattern.compile(
                    regexBody,
                    if (isCaseInsensitive) Pattern.CASE_INSENSITIVE else 0
                )
            }

            // 2. Standard Tampermonkey / Chrome Match Pattern: <scheme>://<host>/<path>
            if (trimmed.contains("://")) {
                val schemePart = trimmed.substringBefore("://")
                val afterScheme = trimmed.substringAfter("://")
                val hostPart = afterScheme.substringBefore("/")
                val pathPart = if (afterScheme.contains("/")) afterScheme.substringAfter("/") else "*"

                val schemeRegex = when (schemePart) {
                    "*" -> "https?://"
                    "http" -> "http://"
                    "https" -> "https://"
                    "file" -> "file://"
                    "ftp" -> "ftp://"
                    else -> "${Pattern.quote(schemePart)}://"
                }

                val hostRegex = when {
                    hostPart == "*" -> "[^/]+"
                    hostPart.startsWith("*.") -> {
                        val baseDomain = hostPart.removePrefix("*.")
                        "([a-zA-Z0-9.-]+\\.)?${Pattern.quote(baseDomain)}"
                    }
                    hostPart.contains("*") -> {
                        buildWildcardHostRegex(hostPart)
                    }
                    else -> Pattern.quote(hostPart)
                }

                val pathRegex = if (pathPart == "*") {
                    ".*"
                } else {
                    buildWildcardPathRegex(pathPart)
                }

                return Pattern.compile("^$schemeRegex$hostRegex(/$pathRegex)?$", Pattern.CASE_INSENSITIVE)
            }

            // 3. Fallback generic wildcard matcher
            var regexStr = Pattern.quote(trimmed)
                .replace("\\*", ".*")
                .replace("\\?", ".")

            if (!regexStr.startsWith(".*") && !regexStr.startsWith("http")) {
                regexStr = ".*$regexStr"
            }
            if (!regexStr.endsWith(".*")) {
                regexStr = "$regexStr.*"
            }

            return Pattern.compile("^$regexStr$", Pattern.CASE_INSENSITIVE)
        }

        private fun buildWildcardHostRegex(host: String): String {
            val parts = host.split("*")
            return parts.joinToString("[^/]*") { Pattern.quote(it) }
        }

        private fun buildWildcardPathRegex(path: String): String {
            val parts = path.split("*")
            return parts.joinToString(".*") { Pattern.quote(it) }
        }

        fun parseMetadata(code: String): ParsedScriptMetadata {
            var name = "Untitled Script"
            var description = ""
            var author = "Tampermonkey User"
            var version = "1.0"
            var runAt = "document-idle"
            val includes = mutableListOf<String>()
            val excludes = mutableListOf<String>()

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
                    val parts = content.split(Regex("\\s+"), 2)
                    if (parts.isNotEmpty()) {
                        val key = parts[0].lowercase()
                        val value = if (parts.size > 1) parts[1].trim() else ""
                        when (key) {
                            "@name" -> if (value.isNotEmpty()) name = value
                            "@description" -> if (value.isNotEmpty()) description = value
                            "@author" -> if (value.isNotEmpty()) author = value
                            "@version" -> if (value.isNotEmpty()) version = value
                            "@run-at" -> {
                                val rawRunAt = value.lowercase()
                                runAt = when {
                                    rawRunAt.contains("document-start") -> "document-start"
                                    rawRunAt.contains("document-body") -> "document-body"
                                    rawRunAt.contains("document-end") -> "document-end"
                                    else -> "document-idle"
                                }
                            }
                            "@match" -> if (value.isNotEmpty()) includes.add(value)
                            "@include" -> if (value.isNotEmpty()) includes.add(value)
                            "@exclude" -> if (value.isNotEmpty()) excludes.add("!$value")
                        }
                    }
                }
            }

            val allPatterns = (includes + excludes).distinct()
            val patternString = if (allPatterns.isEmpty()) "*://*/*" else allPatterns.joinToString(", ")

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

