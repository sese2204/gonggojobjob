package org.example.kotlinai.dto.response

data class ActivitySearchResponse(
    val activities: List<ActivityResult>,
    val totalCount: Int,
    val newTodayCount: Int,
)
