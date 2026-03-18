package org.example.kotlinai.dto.request

data class CreateConversationRequest(
    val userId: Long,
    val title: String? = null,
)

data class SendMessageRequest(
    val content: String,
)
