package org.example.kotlinai.dto.request

data class CreateBookmarkRequest(
    val jobListingId: Long? = null,
    val recommendedJobId: Long? = null,
)
