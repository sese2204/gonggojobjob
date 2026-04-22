package org.example.kotlinai.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

@Service
class UpstageGroundednessService(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(UpstageGroundednessService::class.java)

    @Value("\${upstage.api.key:}")
    private lateinit var apiKey: String

    // Chat Completions 엔드포인트로 groundedness-check 모델 사용
    private val baseUrl = "https://api.upstage.ai/v1/chat/completions"

    enum class Groundedness { GROUNDED, NOT_GROUNDED, NOT_SURE }

    private val restClient: RestClient by lazy {
        RestClient.builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(30))
                    setReadTimeout(Duration.ofSeconds(30))
                }
            )
            .build()
    }

    /**
     * context: 검색 결과 문서 (공고 내용) → user 메시지
     * answer:  질의 의도를 서술한 문장            → assistant 메시지
     *
     * grounded    → 관련 (score 2)
     * notSure     → 부분 관련 (score 1)
     * notGrounded → 무관 (score 0)
     */
    fun check(context: String, answer: String): Groundedness {
        val body = mapOf(
            "model" to "groundedness-check",
            "messages" to listOf(
                mapOf("role" to "user", "content" to context),
                mapOf("role" to "assistant", "content" to answer),
            ),
        )
        return try {
            val raw = restClient.post()
                .uri(baseUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String::class.java)
                ?: return Groundedness.NOT_SURE

            @Suppress("UNCHECKED_CAST")
            val response: Map<String, Any> = objectMapper.readValue(raw)
            val choices = response["choices"] as? List<*> ?: return Groundedness.NOT_SURE
            val first = choices.firstOrNull() as? Map<*, *> ?: return Groundedness.NOT_SURE
            val message = first["message"] as? Map<*, *> ?: return Groundedness.NOT_SURE
            val content = message["content"] as? String ?: return Groundedness.NOT_SURE

            when (content.trim()) {
                "grounded" -> Groundedness.GROUNDED
                "notGrounded" -> Groundedness.NOT_GROUNDED
                else -> Groundedness.NOT_SURE
            }
        } catch (e: RestClientException) {
            log.warn("[Groundedness] API 호출 실패: {}", e.message)
            Groundedness.NOT_SURE
        }
    }

    fun toScore(groundedness: Groundedness): Int = when (groundedness) {
        Groundedness.GROUNDED -> 2
        Groundedness.NOT_SURE -> 1
        Groundedness.NOT_GROUNDED -> 0
    }
}
