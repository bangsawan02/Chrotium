package com.example.data.model

enum class SuggestionType {
    QUERY,
    BOOKMARK,
    HISTORY,
    DIRECT_URL
}

data class SuggestionItem(
    val title: String,
    val destinationUrl: String,
    val type: SuggestionType,
    val subtitle: String? = null
)
