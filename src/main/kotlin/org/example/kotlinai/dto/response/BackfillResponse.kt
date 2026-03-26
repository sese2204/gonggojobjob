package org.example.kotlinai.dto.response

data class BackfillResponse(
    val processedCount: Int,
    val failedCount: Int,
    val totalUnembedded: Int,
)
