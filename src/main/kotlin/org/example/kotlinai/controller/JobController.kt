package org.example.kotlinai.controller

import org.example.kotlinai.dto.request.JobSearchRequest
import org.example.kotlinai.dto.response.JobSearchResponse
import org.example.kotlinai.service.JobService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Jobs", description = "채용공고 검색 API")
@RestController
@RequestMapping("/api/jobs")
class JobController(
    private val jobService: JobService,
) {

    @Operation(summary = "채용공고 검색", description = "태그 또는 자연어 쿼리로 최신 공고 10건을 반환합니다.")
    @PostMapping("/search")
    fun search(@RequestBody request: JobSearchRequest): JobSearchResponse =
        jobService.searchJobs(request)
}
