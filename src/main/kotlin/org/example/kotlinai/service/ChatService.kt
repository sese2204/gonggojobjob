package org.example.kotlinai.service

import org.example.kotlinai.dto.request.SendMessageRequest
import org.example.kotlinai.dto.response.ConversationResponse
import org.example.kotlinai.dto.response.MessageResponse
import org.example.kotlinai.entity.Conversation
import org.example.kotlinai.entity.Message
import org.example.kotlinai.entity.MessageRole
import org.example.kotlinai.repository.ConversationRepository
import org.example.kotlinai.repository.MessageRepository
import org.example.kotlinai.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ChatService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun createConversation(userId: Long, title: String?): ConversationResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("유저를 찾을 수 없습니다: $userId") }

        val conversation = conversationRepository.save(
            Conversation(user = user, title = title)
        )
        return conversation.toResponse()
    }

    fun getConversations(userId: Long): List<ConversationResponse> =
        conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)
            .map { it.toResponse() }

    @Transactional
    fun sendMessage(conversationId: Long, request: SendMessageRequest): MessageResponse {
        val conversation = conversationRepository.findById(conversationId)
            .orElseThrow { NoSuchElementException("대화를 찾을 수 없습니다: $conversationId") }

        messageRepository.save(
            Message(conversation = conversation, role = MessageRole.USER, content = request.content)
        )

        // TODO: 실제 AI API 호출로 교체
        val aiReply = "[AI 응답 준비 중] 입력: ${request.content}"

        val assistantMessage = messageRepository.save(
            Message(conversation = conversation, role = MessageRole.ASSISTANT, content = aiReply)
        )

        conversation.updatedAt = LocalDateTime.now()

        return assistantMessage.toResponse()
    }

    fun getMessages(conversationId: Long): List<MessageResponse> =
        messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId)
            .map { it.toResponse() }
}

fun Conversation.toResponse() = ConversationResponse(id = id, userId = user.id, title = title)
fun Message.toResponse() = MessageResponse(id = id, role = role.name, content = content)
