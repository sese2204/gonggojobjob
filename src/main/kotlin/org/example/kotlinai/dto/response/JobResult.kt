package org.example.kotlinai.dto.response

data class JobResult(
    val id: String,
    val title: String,
    val company: String,
    val match: Int,
    val reason: String,
    val url: String,
)
