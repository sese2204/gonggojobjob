package org.example.kotlinai.dto.response

data class StatsResponse(
    val totalCount: Long,
    val newTodayCount: Long,
    val activityTotalCount: Long,
    val activityNewTodayCount: Long,
)
