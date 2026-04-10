package org.example.kotlinai.dto.request

data class ActivitySearchRequest(
    val tags: List<String> = emptyList(),
    val query: String = "",
)
