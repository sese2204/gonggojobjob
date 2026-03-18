package org.example.kotlinai.dto.response

data class ConversationResponse(
    val id: Long,
    val userId: Long,
    val title: String?,
)

data class MessageResponse(
    val id: Long,
    val role: String,
    val content: String,
)
