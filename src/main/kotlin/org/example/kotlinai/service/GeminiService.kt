package org.example.kotlinai.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.global.exception.AiServiceException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI
import java.time.Duration

data class AiJobSummary(
    val id: String,
    val title: String,
    val company: String,
    val description: String?,
)

data class AiActivitySummary(
    val id: String,
    val title: String,
    val organizer: String,
    val category: String?,
    val description: String?,
)

data class AiMatchResponse(
    val results: List<AiMatchResult>,
    val inputChars: Int,
)

@Service
class GeminiService(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(GeminiService::class.java)
    @Value("\${gemini.api.key}")
    private lateinit var apiKey: String

    @Value("\${gemini.api.url}")
    private lateinit var apiUrl: String

    @Value("\${gemini.api.timeout-seconds}")
    private var timeoutSeconds: Long = 30

    private val restClient: RestClient by lazy {
        RestClient.builder()
            .requestFactory(
                org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(timeoutSeconds))
                    setReadTimeout(Duration.ofSeconds(timeoutSeconds))
                }
            )
            .build()
    }

    fun matchJobs(
        tags: List<String>,
        query: String,
        listings: List<AiJobSummary>,
    ): AiMatchResponse = callGeminiApi(buildPrompt(tags, query, listings))

    fun matchActivities(
        tags: List<String>,
        query: String,
        listings: List<AiActivitySummary>,
    ): AiMatchResponse = callGeminiApi(buildActivityPrompt(tags, query, listings))

    private fun callGeminiApi(prompt: String): AiMatchResponse {
        val requestBody = mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt)))
            ),
            "generationConfig" to mapOf(
                "responseMimeType" to "application/json"
            ),
        )

        val requestJson = objectMapper.writeValueAsString(requestBody)
        log.info("[Gemini] request chars={} est_tokens={}", requestJson.length, requestJson.length / 3)

        val rawResponse = try {
            restClient.post()
                .uri(URI.create("$apiUrl?key=$apiKey"))
                .header("Content-Type", "application/json")
                .body(requestJson)
                .retrieve()
                .body(Map::class.java)
        } catch (e: RestClientException) {
            throw AiServiceException("Gemini API 호출 실패: ${e.message}", e)
        } catch (e: Exception) {
            throw AiServiceException("AI 서비스 오류: ${e.message}", e)
        } ?: throw AiServiceException("AI 서비스가 빈 응답을 반환했습니다.")

        val jsonText = extractJsonText(rawResponse)
            ?: throw AiServiceException("AI 응답에서 JSON을 추출할 수 없습니다.")

        val results = try {
            objectMapper.readValue<List<AiMatchResult>>(jsonText)
        } catch (e: Exception) {
            throw AiServiceException("AI 응답 JSON 파싱 실패: ${e.message}", e)
        }
        return AiMatchResponse(results = results, inputChars = requestJson.length)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractJsonText(response: Map<*, *>): String? {
        return try {
            val candidates = response["candidates"] as? List<*> ?: return null
            val firstCandidate = candidates.firstOrNull() as? Map<*, *> ?: return null
            val content = firstCandidate["content"] as? Map<*, *> ?: return null
            val parts = content["parts"] as? List<*> ?: return null
            val firstPart = parts.firstOrNull() as? Map<*, *> ?: return null
            firstPart["text"] as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun buildPrompt(tags: List<String>, query: String, listings: List<AiJobSummary>): String {
        val listingsJson = objectMapper.writeValueAsString(listings)
        return """
            당신은 채용 공고 매칭 전문가입니다. 아래 사용자 검색 조건과 공고 목록을 보고, 각 공고에 대해 매칭 점수(0-100)와 한국어로 된 매칭 이유를 평가해주세요.

            사용자 조건:
            - 기술 태그: ${objectMapper.writeValueAsString(tags)}
            - 검색어: $query

            공고 목록:
            $listingsJson

            반드시 아래 형식의 JSON 배열만 반환하세요 (다른 텍스트 없이):
            [{"id":"공고id","match":점수,"reason":"한국어 이유"}]

            각 공고의 id는 입력된 id 그대로 사용하세요. match는 0-100 사이의 정수이며, reason은 왜 이 공고가 사용자의 조건과 일치하거나 일치하지 않는지 1-2문장으로 설명하는 한국어 문장이어야 합니다.
        """.trimIndent()
    }

    private fun buildActivityPrompt(tags: List<String>, query: String, listings: List<AiActivitySummary>): String {
        val listingsJson = objectMapper.writeValueAsString(listings)
        return """
            당신은 공모전/대외활동 매칭 전문가입니다. 아래 사용자 검색 조건과 활동 목록을 보고, 각 활동에 대해 매칭 점수(0-100)와 한국어로 된 매칭 이유를 평가해주세요.

            사용자 조건:
            - 관심 태그: ${objectMapper.writeValueAsString(tags)}
            - 검색어: $query

            활동 목록:
            $listingsJson

            반드시 아래 형식의 JSON 배열만 반환하세요 (다른 텍스트 없이):
            [{"id":"활동id","match":점수,"reason":"한국어 이유"}]

            각 활동의 id는 입력된 id 그대로 사용하세요. match는 0-100 사이의 정수이며, reason은 왜 이 활동이 사용자의 조건과 일치하거나 일치하지 않는지 1-2문장으로 설명하는 한국어 문장이어야 합니다.
        """.trimIndent()
    }
}
