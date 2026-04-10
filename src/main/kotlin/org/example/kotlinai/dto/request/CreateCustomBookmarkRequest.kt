package org.example.kotlinai.dto.request

import org.example.kotlinai.entity.BookmarkType

data class CreateCustomBookmarkRequest(
    val title: String,
    val company: String,
    val url: String? = null,
    val description: String? = null,
    val type: BookmarkType = BookmarkType.JOB,
    val category: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
)
