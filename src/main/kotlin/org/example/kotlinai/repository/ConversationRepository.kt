package org.example.kotlinai.repository

import org.example.kotlinai.entity.Conversation
import org.springframework.data.jpa.repository.JpaRepository

interface ConversationRepository : JpaRepository<Conversation, Long> {
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: Long): List<Conversation>
}
