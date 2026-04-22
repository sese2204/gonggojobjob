package org.example.kotlinai.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.example.kotlinai.config.UpstageEmbeddingProperties
import org.example.kotlinai.global.exception.EmbeddingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

@Service
class UpstageEmbeddingService(
    private val objectMapper: ObjectMapper,
    private val upstageEmbeddingProperties: UpstageEmbeddingProperties,
) {
    private val log = LoggerFactory.getLogger(UpstageEmbeddingService::class.java)

    @Value("\${upstage.api.key:}")
    private lateinit var apiKey: String

    private val baseUrl = "https://api.upstage.ai/v1/embeddings"

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

    val passageModelName: String get() = upstageEmbeddingProperties.passageModel

    fun embedQuery(text: String): List<Float> =
        embedWithModel(text, upstageEmbeddingProperties.queryModel)

    fun embedPassage(text: String): List<Float> =
        embedWithModel(text, upstageEmbeddingProperties.passageModel)

    fun embedPassages(texts: List<String>): List<List<Float>> {
        if (texts.isEmpty()) return emptyList()
        return texts.chunked(upstageEmbeddingProperties.batchSize).flatMap { batch ->
            val body = mapOf("model" to upstageEmbeddingProperties.passageModel, "input" to batch)
            extractBatch(callApi(body), batch.size)
        }
    }

    private fun embedWithModel(text: String, model: String): List<Float> {
        val body = mapOf("model" to model, "input" to text)
        return extractSingle(callApi(body))
    }

    private fun callApi(body: Any): Map<String, Any> =
        try {
            val raw = restClient.post()
                .uri(baseUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String::class.java)
                ?: throw EmbeddingException("Upstage 임베딩 API가 빈 응답을 반환했습니다.")
            objectMapper.readValue(raw)
        } catch (e: EmbeddingException) {
            throw e
        } catch (e: RestClientException) {
            throw EmbeddingException("Upstage 임베딩 API 호출 실패: ${e.message}", e)
        }

    @Suppress("UNCHECKED_CAST")
    private fun extractSingle(response: Map<String, Any>): List<Float> {
        val data = response["data"] as? List<*>
            ?: throw EmbeddingException("Upstage 응답에서 data 필드를 찾을 수 없습니다.")
        val first = data.firstOrNull() as? Map<*, *>
            ?: throw EmbeddingException("Upstage data 배열이 비어 있습니다.")
        val values = first["embedding"] as? List<*>
            ?: throw EmbeddingException("Upstage 응답에서 embedding 필드를 찾을 수 없습니다.")
        return values.map { (it as Number).toFloat() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractBatch(response: Map<String, Any>, expectedSize: Int): List<List<Float>> {
        val data = response["data"] as? List<*>
            ?: throw EmbeddingException("Upstage 배치 응답에서 data 필드를 찾을 수 없습니다.")
        if (data.size != expectedSize) {
            log.warn("[Upstage] 배치 응답 크기 불일치: expected={}, actual={}", expectedSize, data.size)
        }
        return data.sortedBy { (it as Map<*, *>)["index"] as? Int ?: 0 }.map { item ->
            val entry = item as? Map<*, *>
                ?: throw EmbeddingException("Upstage 배치 응답 항목 파싱 실패")
            val values = entry["embedding"] as? List<*>
                ?: throw EmbeddingException("Upstage 배치 항목에서 embedding 필드를 찾을 수 없습니다.")
            values.map { (it as Number).toFloat() }
        }
    }
}