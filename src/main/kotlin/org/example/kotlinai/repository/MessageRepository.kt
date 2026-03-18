package org.example.kotlinai.repository

import org.example.kotlinai.entity.Message
import org.springframework.data.jpa.repository.JpaRepository

interface MessageRepository : JpaRepository<Message, Long> {
    fun findAllByConversationIdOrderByCreatedAtAsc(conversationId: Long): List<Message>
}
