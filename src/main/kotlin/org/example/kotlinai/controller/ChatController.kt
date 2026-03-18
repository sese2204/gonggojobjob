package org.example.kotlinai.controller

import org.example.kotlinai.dto.request.CreateConversationRequest
import org.example.kotlinai.dto.request.SendMessageRequest
import org.example.kotlinai.dto.response.ConversationResponse
import org.example.kotlinai.dto.response.MessageResponse
import org.example.kotlinai.service.ChatService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/chat")
class ChatController(
    private val chatService: ChatService,
) {

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    fun createConversation(@RequestBody request: CreateConversationRequest): ConversationResponse =
        chatService.createConversation(request)

    @GetMapping("/conversations")
    fun getConversations(@RequestParam userId: Long): List<ConversationResponse> =
        chatService.getConversations(userId)

    @PostMapping("/conversations/{conversationId}/messages")
    fun sendMessage(
        @PathVariable conversationId: Long,
        @RequestBody request: SendMessageRequest,
    ): MessageResponse =
        chatService.sendMessage(conversationId, request)

    @GetMapping("/conversations/{conversationId}/messages")
    fun getMessages(@PathVariable conversationId: Long): List<MessageResponse> =
        chatService.getMessages(conversationId)
}
