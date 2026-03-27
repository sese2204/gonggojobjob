package org.example.kotlinai.dto.response

import java.time.LocalDateTime

data class CheerResponse(
    val id: Long,
    val nickname: String,
    val content: String,
    val createdAt: LocalDateTime,
)
