package org.example.kotlinai.dto.response

import java.time.LocalDateTime

data class RecommendedJobResponse(
    val id: Long,
    val jobListingId: Long,
    val title: String,
    val company: String,
    val url: String,
    val matchScore: Int,
    val reason: String,
    val searchedAt: LocalDateTime,
)
