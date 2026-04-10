package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.response.ActivitySearchHistoryDetailResponse
import org.example.kotlinai.dto.response.ActivitySearchHistoryResponse
import org.example.kotlinai.dto.response.RecommendedActivityResponse
import org.example.kotlinai.global.exception.ErrorResponse
import org.example.kotlinai.service.ActivitySearchHistoryService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Activity Search History", description = "활동 검색 기록 및 추천 활동 조회 API")
@RestController
@RequestMapping("/api/activity-search-history")
class ActivitySearchHistoryController(
    private val activitySearchHistoryService: ActivitySearchHistoryService,
) {

    @Operation(
        summary = "활동 검색 기록 목록 조회",
        description = "사용자의 활동 검색 기록을 최신순으로 페이지네이션하여 반환합니다.",
    )
    @GetMapping
    fun getSearchHistories(
        @AuthenticationPrincipal userId: Long,
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<ActivitySearchHistoryResponse> =
        activitySearchHistoryService.getSearchHistories(userId, pageable)

    @Operation(
        summary = "활동 검색 기록 상세 조회",
        description = "특정 활동 검색 기록과 해당 검색에서 추천된 활동 목록을 반환합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "404",
            description = "검색 기록을 찾을 수 없음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{searchId}")
    fun getSearchDetail(@PathVariable searchId: Long): ActivitySearchHistoryDetailResponse =
        activitySearchHistoryService.getSearchDetail(searchId)

    @Operation(
        summary = "추천 활동 전체 조회",
        description = "사용자의 모든 활동 검색에서 추천된 활동을 AI 매칭 점수 내림차순으로 페이지네이션하여 반환합니다.",
    )
    @GetMapping("/recommended-activities")
    fun getRecommendedActivities(
        @AuthenticationPrincipal userId: Long,
        @PageableDefault(size = 20, sort = ["matchScore"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): Page<RecommendedActivityResponse> =
        activitySearchHistoryService.getRecommendedActivities(userId, pageable)

    @Operation(
        summary = "추천 활동 삭제",
        description = "사용자의 저장된 추천 활동을 삭제합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 성공"),
        ApiResponse(
            responseCode = "404",
            description = "추천 활동을 찾을 수 없음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @DeleteMapping("/recommended-activities/{recommendedActivityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRecommendedActivity(
        @AuthenticationPrincipal userId: Long,
        @PathVariable recommendedActivityId: Long,
    ) = activitySearchHistoryService.deleteRecommendedActivity(userId, recommendedActivityId)
}
