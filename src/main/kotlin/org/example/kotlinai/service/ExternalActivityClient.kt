package org.example.kotlinai.service

import org.example.kotlinai.dto.response.ExternalActivityDto

interface ExternalActivityClient {
    fun sourceName(): String
    fun fetchActivities(): List<ExternalActivityDto>
}
