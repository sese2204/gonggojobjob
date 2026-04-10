package org.example.kotlinai.dto.response

data class ExternalActivityDto(
    val sourceId: String,
    val title: String,
    val organizer: String,
    val url: String,
    val category: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val description: String? = null,
)
