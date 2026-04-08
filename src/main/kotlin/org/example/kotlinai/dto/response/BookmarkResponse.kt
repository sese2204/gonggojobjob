package org.example.kotlinai.dto.response

import org.example.kotlinai.entity.ApplicationStatus
import java.time.LocalDateTime

data class BookmarkResponse(
    val id: Long,
    val jobListingId: Long?,
    val title: String,
    val company: String,
    val url: String?,
    val description: String?,
    val status: ApplicationStatus,
    val memo: String?,
    val bookmarkedAt: LocalDateTime,
)
