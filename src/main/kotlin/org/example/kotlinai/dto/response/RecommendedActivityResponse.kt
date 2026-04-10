package org.example.kotlinai.dto.response

import java.time.LocalDateTime

data class RecommendedActivityResponse(
    val id: Long,
    val activityListingId: Long,
    val title: String,
    val organizer: String,
    val url: String,
    val category: String?,
    val startDate: String?,
    val endDate: String?,
    val matchScore: Int,
    val reason: String,
    val searchedAt: LocalDateTime,
)
