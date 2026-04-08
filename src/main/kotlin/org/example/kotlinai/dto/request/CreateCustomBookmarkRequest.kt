package org.example.kotlinai.dto.request

data class CreateCustomBookmarkRequest(
    val title: String,
    val company: String,
    val url: String? = null,
    val description: String? = null,
)
