package org.example.kotlinai.dto.response

import java.time.LocalDateTime

data class ActivitySearchHistoryResponse(
    val id: Long,
    val tags: List<String>,
    val query: String?,
    val resultCount: Int,
    val searchedAt: LocalDateTime,
)
