package org.example.kotlinai.dto.response

data class ActivityIngestionResponse(
    val sourceName: String,
    val newCount: Int,
    val duplicateCount: Int,
    val failedCount: Int,
    val success: Boolean,
)

data class ActivityBackfillResponse(
    val processedCount: Int,
    val failedCount: Int,
    val totalUnembedded: Int,
)
