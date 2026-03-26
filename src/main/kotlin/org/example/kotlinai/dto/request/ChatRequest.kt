package org.example.kotlinai.dto.request

data class CreateConversationRequest(
    val title: String? = null,
)

data class SendMessageRequest(
    val content: String,
)
