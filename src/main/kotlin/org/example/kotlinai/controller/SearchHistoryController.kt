package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.response.RecommendedJobResponse
import org.example.kotlinai.dto.response.SearchHistoryDetailResponse
import org.example.kotlinai.dto.response.SearchHistoryResponse
import org.example.kotlinai.global.exception.ErrorResponse
import org.example.kotlinai.service.SearchHistoryService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Search History", description = "검색 기록 및 추천 공고 조회 API")
@RestController
@RequestMapping("/api/search-history")
class SearchHistoryController(
    private val searchHistoryService: SearchHistoryService,
) {

    @Operation(
        summary = "검색 기록 목록 조회",
        description = "사용자의 검색 기록을 최신순으로 페이지네이션하여 반환합니다.",
    )
    @GetMapping
    fun getSearchHistories(
        @AuthenticationPrincipal userId: Long,
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<SearchHistoryResponse> =
        searchHistoryService.getSearchHistories(userId, pageable)

    @Operation(
        summary = "검색 기록 상세 조회",
        description = "특정 검색 기록과 해당 검색에서 추천된 공고 목록을 반환합니다.",
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
    fun getSearchDetail(@PathVariable searchId: Long): SearchHistoryDetailResponse =
        searchHistoryService.getSearchDetail(searchId)

    @Operation(
        summary = "추천 공고 전체 조회",
        description = "사용자의 모든 검색에서 추천된 공고를 최신순으로 페이지네이션하여 반환합니다.",
    )
    @GetMapping("/recommended-jobs")
    fun getRecommendedJobs(
        @AuthenticationPrincipal userId: Long,
        @PageableDefault(size = 20, sort = ["searchHistory.searchedAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): Page<RecommendedJobResponse> =
        searchHistoryService.getRecommendedJobs(userId, pageable)
}
