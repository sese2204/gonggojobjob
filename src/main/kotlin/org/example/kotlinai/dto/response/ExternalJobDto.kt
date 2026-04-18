package org.example.kotlinai.dto.response

data class ExternalJobDto(
    val sourceId: String,
    val title: String,
    val company: String,
    val url: String,
    val description: String? = null,
    val deadline: String? = null,
)
