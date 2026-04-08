package org.example.kotlinai.dto.request

import org.example.kotlinai.entity.ApplicationStatus

data class UpdateBookmarkRequest(
    val status: ApplicationStatus? = null,
    val memo: String? = null,
)
