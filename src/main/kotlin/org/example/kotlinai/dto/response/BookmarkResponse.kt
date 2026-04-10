package org.example.kotlinai.dto.response

import org.example.kotlinai.entity.ApplicationStatus
import org.example.kotlinai.entity.BookmarkType
import java.time.LocalDateTime

data class BookmarkResponse(
    val id: Long,
    val type: BookmarkType,
    val jobListingId: Long?,
    val activityListingId: Long?,
    val title: String,
    val company: String,
    val url: String?,
    val description: String?,
    val category: String?,
    val startDate: String?,
    val endDate: String?,
    val status: ApplicationStatus,
    val memo: String?,
    val bookmarkedAt: LocalDateTime,
)
