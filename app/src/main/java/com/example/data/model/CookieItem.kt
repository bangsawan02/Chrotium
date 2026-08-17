package com.example.data.model

data class CookieItem(
    val name: String,
    val value: String,
    val domain: String = "",
    val path: String = "/",
    val rawString: String = ""
)
