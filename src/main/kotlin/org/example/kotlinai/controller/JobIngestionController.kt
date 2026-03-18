package org.example.kotlinai.controller

import org.example.kotlinai.dto.response.JobIngestionResponse
import org.example.kotlinai.service.JobIngestionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Jobs", description = "채용공고 검색 API")
@RestController
@RequestMapping("/api/jobs")
class JobIngestionController(
    private val jobIngestionService: JobIngestionService,
) {

    @Operation(summary = "공고 수집", description = "외부 소스에서 채용공고를 가져와 DB에 저장합니다.")
    @PostMapping("/ingest")
    fun ingest(): JobIngestionResponse =
        JobIngestionResponse(count = jobIngestionService.ingestJobs())
}
