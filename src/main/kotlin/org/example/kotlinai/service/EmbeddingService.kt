package org.example.kotlinai.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.kotlinai.config.GeminiEmbeddingProperties
import org.example.kotlinai.global.exception.EmbeddingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI
import java.time.Duration

@Service
class EmbeddingService(
    private val objectMapper: ObjectMapper,
    private val embeddingProperties: GeminiEmbeddingProperties,
) {
    private val log = LoggerFactory.getLogger(EmbeddingService::class.java)

    @Value("\${gemini.api.key}")
    private lateinit var apiKey: String

    @Value("\${gemini.api.timeout-seconds}")
    private var timeoutSeconds: Long = 30

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

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

    fun embedText(text: String, taskType: String = "RETRIEVAL_DOCUMENT"): List<Float> {
        val requestBody = mapOf(
            "model" to "models/${embeddingProperties.model}",
            "content" to mapOf("parts" to listOf(mapOf("text" to text))),
            "taskType" to taskType,
            "outputDimensionality" to embeddingProperties.dimensions,
        )

        val response = callEmbedApi(
            "$baseUrl/${embeddingProperties.model}:embedContent",
            requestBody,
        )

        return extractSingleEmbedding(response)
    }

    fun embedTexts(texts: List<String>, taskType: String = "RETRIEVAL_DOCUMENT"): List<List<Float>> {
        if (texts.isEmpty()) return emptyList()

        return texts.chunked(embeddingProperties.batchSize).flatMap { batch ->
            val requests = batch.map { text ->
                mapOf(
                    "model" to "models/${embeddingProperties.model}",
                    "content" to mapOf("parts" to listOf(mapOf("text" to text))),
                    "taskType" to taskType,
                    "outputDimensionality" to embeddingProperties.dimensions,
                )
            }
            val requestBody = mapOf("requests" to requests)

            val response = callEmbedApi(
                "$baseUrl/${embeddingProperties.model}:batchEmbedContents",
                requestBody,
            )

            extractBatchEmbeddings(response)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractSingleEmbedding(response: Map<*, *>): List<Float> {
        val embedding = response["embedding"] as? Map<*, *>
            ?: throw EmbeddingException("임베딩 응답에서 embedding 필드를 찾을 수 없습니다.")
        val values = embedding["values"] as? List<*>
            ?: throw EmbeddingException("임베딩 응답에서 values 필드를 찾을 수 없습니다.")
        return values.map { (it as Number).toFloat() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractBatchEmbeddings(response: Map<*, *>): List<List<Float>> {
        val embeddings = response["embeddings"] as? List<*>
            ?: throw EmbeddingException("배치 임베딩 응답에서 embeddings 필드를 찾을 수 없습니다.")
        return embeddings.map { item ->
            val embedding = item as? Map<*, *>
                ?: throw EmbeddingException("배치 임베딩 항목 파싱 실패")
            val values = embedding["values"] as? List<*>
                ?: throw EmbeddingException("배치 임베딩 values 파싱 실패")
            values.map { (it as Number).toFloat() }
        }
    }

    private fun callEmbedApi(url: String, requestBody: Any): Map<*, *> =
        try {
            restClient.post()
                .uri(URI.create("$url?key=$apiKey"))
                .header("Content-Type", "application/json")
                .body(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .body(Map::class.java)
                ?: throw EmbeddingException("임베딩 API가 빈 응답을 반환했습니다.")
        } catch (e: EmbeddingException) {
            throw e
        } catch (e: RestClientException) {
            throw EmbeddingException("임베딩 API 호출 실패: ${e.message}", e)
        } catch (e: Exception) {
            throw EmbeddingException("임베딩 서비스 오류: ${e.message}", e)
        }

    companion object {
        fun List<Float>.toVectorString(): String =
            joinToString(",", prefix = "[", postfix = "]")
    }
}
