package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.request.CreateConversationRequest
import org.example.kotlinai.dto.request.SendMessageRequest
import org.example.kotlinai.dto.response.ConversationResponse
import org.example.kotlinai.dto.response.MessageResponse
import org.example.kotlinai.service.ChatService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@Tag(name = "Chat", description = "대화 및 메시지 관리 API")
@RestController
@RequestMapping("/api/chat")
class ChatController(
    private val chatService: ChatService,
) {

    @Operation(summary = "대화 생성", description = "새로운 대화를 생성합니다.")
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    fun createConversation(@RequestBody request: CreateConversationRequest): ConversationResponse =
        chatService.createConversation(request)

    @Operation(summary = "대화 목록 조회", description = "특정 유저의 모든 대화를 최신순으로 반환합니다.")
    @GetMapping("/conversations")
    fun getConversations(@RequestParam userId: Long): List<ConversationResponse> =
        chatService.getConversations(userId)

    @Operation(summary = "메시지 전송", description = "대화에 메시지를 전송하고 AI 응답을 반환합니다.")
    @PostMapping("/conversations/{conversationId}/messages")
    fun sendMessage(
        @PathVariable conversationId: Long,
        @RequestBody request: SendMessageRequest,
    ): MessageResponse =
        chatService.sendMessage(conversationId, request)

    @Operation(summary = "메시지 목록 조회", description = "대화의 모든 메시지를 시간순으로 반환합니다.")
    @GetMapping("/conversations/{conversationId}/messages")
    fun getMessages(@PathVariable conversationId: Long): List<MessageResponse> =
        chatService.getMessages(conversationId)
}
