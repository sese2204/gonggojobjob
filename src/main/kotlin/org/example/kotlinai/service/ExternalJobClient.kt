package org.example.kotlinai.service

import org.example.kotlinai.dto.response.ExternalJobDto

interface ExternalJobClient {
    fun fetchJobs(): List<ExternalJobDto>
}
