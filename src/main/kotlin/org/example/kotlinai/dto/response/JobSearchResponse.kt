package org.example.kotlinai.dto.response

data class JobSearchResponse(
    val jobs: List<JobResult>,
    val totalCount: Int,
    val newTodayCount: Int,
)
