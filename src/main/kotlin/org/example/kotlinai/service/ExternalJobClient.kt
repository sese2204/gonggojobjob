package org.example.kotlinai.service

import org.example.kotlinai.dto.response.ExternalJobDto

interface ExternalJobClient {
    fun sourceName(): String
    fun fetchJobs(): List<ExternalJobDto>
    fun supportsFullSync(): Boolean = false
}
