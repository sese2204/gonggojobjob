package org.example.kotlinai.service

import com.fasterxml.jackson.databind.ObjectMapper
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

    private val baseUrl = "https://api.upstage.ai/v1/groundedness-check"

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
     * context: 검색 결과 문서 (공고 내용)
     * answer:  질의 의도를 서술한 문장 ("이 활동은 [query] 관련 활동입니다")
     *
     * grounded    → 관련 (score 2)
     * notSure     → 부분 관련 (score 1)
     * notGrounded → 무관 (score 0)
     */
    fun check(context: String, answer: String): Groundedness {
        val body = mapOf("context" to context, "answer" to answer)
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
            val response = objectMapper.readValue(raw, Map::class.java) as Map<String, Any>
            when (response["groundedness"] as? String) {
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