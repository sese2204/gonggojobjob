package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.request.JobSearchRequest
import org.example.kotlinai.dto.response.JobSearchResponse
import org.example.kotlinai.global.exception.ErrorResponse
import org.example.kotlinai.service.JobSearchService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Job Search", description = "AI 기반 채용 공고 검색 API")
@RestController
@RequestMapping("/api/jobs")
class JobSearchController(
    private val jobSearchService: JobSearchService,
) {

    @Operation(
        summary = "채용 공고 검색",
        description = "태그 및 자연어 쿼리를 기반으로 AI가 매칭 점수와 이유를 생성하여 공고를 반환합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "검색 성공"),
        ApiResponse(
            responseCode = "400",
            description = "tags와 query 모두 비어있음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "503",
            description = "AI 서비스 일시 불가",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/search")
    fun search(@RequestBody request: JobSearchRequest): JobSearchResponse =
        jobSearchService.search(request)
}
