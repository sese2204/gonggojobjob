package org.example.kotlinai.dto.response

data class ActivityResult(
    val id: String,
    val title: String,
    val organizer: String,
    val category: String?,
    val startDate: String?,
    val endDate: String?,
    val description: String?,
    val url: String,
    val match: Int,
    val reason: String,
)
