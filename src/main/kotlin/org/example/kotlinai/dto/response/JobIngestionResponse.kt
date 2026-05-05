package org.example.kotlinai.dto.response

data class JobIngestionResponse(
    val sourceName: String,
    val newCount: Int,
    val duplicateCount: Int,
    val failedCount: Int,
    val deletedCount: Int,
    val success: Boolean,
)
