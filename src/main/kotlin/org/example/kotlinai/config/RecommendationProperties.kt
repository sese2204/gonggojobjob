package org.example.kotlinai.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "recommendation")
data class RecommendationProperties(
    val itemsPerCategory: Int = 5,
)
