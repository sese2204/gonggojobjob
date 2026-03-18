package org.example.kotlinai.dto.response

data class JobResponse(
    val id: String,
    val title: String,
    val company: String,
    val match: Int,
    val reason: String,
    val url: String,
)

data class JobSearchResponse(
    val jobs: List<JobResponse>,
    val totalCount: Long,
    val newTodayCount: Long,
)
