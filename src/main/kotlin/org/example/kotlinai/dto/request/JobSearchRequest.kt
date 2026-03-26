package org.example.kotlinai.dto.request

data class JobSearchRequest(
    val tags: List<String> = emptyList(),
    val query: String = "",
    val userId: Long? = null,
)
