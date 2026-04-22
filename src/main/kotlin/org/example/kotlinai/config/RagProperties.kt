package org.example.kotlinai.config

import org.springframework.boot.context.properties.ConfigurationProperties

enum class EmbeddingProvider { GEMINI, UPSTAGE }

@ConfigurationProperties(prefix = "rag.search")
data class RagProperties(
    val topN: Int = 20,
    val keywordWeight: Double = 0.5,
    val vectorWeight: Double = 0.5,
    val embeddingProvider: EmbeddingProvider = EmbeddingProvider.GEMINI,
)

@ConfigurationProperties(prefix = "gemini.embedding")
data class GeminiEmbeddingProperties(
    val model: String = "gemini-embedding-001",
    val dimensions: Int = 768,
    val batchSize: Int = 10,
)

@ConfigurationProperties(prefix = "upstage.embedding")
data class UpstageEmbeddingProperties(
    val queryModel: String = "solar-embedding-1-large-query",
    val passageModel: String = "solar-embedding-1-large-passage",
    val dimensions: Int = 4096,
    val batchSize: Int = 10,
)
