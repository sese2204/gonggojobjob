package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.response.ActivityBackfillResponse
import org.example.kotlinai.dto.response.ActivityIngestionResponse
import org.example.kotlinai.global.exception.ErrorResponse
import org.example.kotlinai.service.ActivityIngestionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Activity Ingestion", description = "공모전/대외활동 수집, 임베딩 백필, 이력 관리 API")
@RestController
@RequestMapping("/api/activity-ingestion")
class ActivityIngestionController(
    private val activityIngestionService: ActivityIngestionService,
) {

    @Operation(
        summary = "공모전/대외활동 수집 실행",
        description = "지정된 소스(생략 시 전체)에서 공모전/대외활동을 수집하여 DB에 저장합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "수집 완료"),
        ApiResponse(
            responseCode = "400",
            description = "알 수 없는 소스 이름",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "503",
            description = "외부 API 호출 실패",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/run")
    fun run(@RequestParam(required = false) source: String?): List<ActivityIngestionResponse> =
        activityIngestionService.runIngestion(source)

    @Operation(
        summary = "수집 이력 조회",
        description = "최근 공모전/대외활동 수집 실행 기록을 최신순으로 반환합니다.",
    )
    @GetMapping("/history")
    fun history(): List<ActivityIngestionResponse> =
        activityIngestionService.getHistory()

    @Operation(
        summary = "임베딩 백필",
        description = "임베딩이 없는 기존 공모전/대외활동에 대해 벡터 임베딩을 일괄 생성합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "백필 완료"),
        ApiResponse(
            responseCode = "503",
            description = "임베딩 서비스 불가",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/backfill-embeddings")
    fun backfillEmbeddings(): ActivityBackfillResponse =
        activityIngestionService.backfillEmbeddings()
}
