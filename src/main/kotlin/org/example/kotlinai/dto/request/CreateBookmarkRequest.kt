package org.example.kotlinai.dto.request

data class CreateBookmarkRequest(
    val jobListingId: Long? = null,
    val recommendedJobId: Long? = null,
    val activityListingId: Long? = null,
    val recommendedActivityId: Long? = null,
)
