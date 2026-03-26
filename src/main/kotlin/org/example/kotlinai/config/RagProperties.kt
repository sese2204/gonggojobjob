package org.example.kotlinai.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rag.search")
data class RagProperties(
    val topN: Int = 20,
    val keywordWeight: Double = 0.5,
    val vectorWeight: Double = 0.5,
)

@ConfigurationProperties(prefix = "gemini.embedding")
data class GeminiEmbeddingProperties(
    val model: String = "gemini-embedding-001",
    val dimensions: Int = 768,
    val batchSize: Int = 10,
)
