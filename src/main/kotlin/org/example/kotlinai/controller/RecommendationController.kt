package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.response.CategoryRecommendationResponse
import org.example.kotlinai.service.RecommendationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendations", description = "카테고리별 추천 API")
class RecommendationController(
    private val recommendationService: RecommendationService,
) {

    @GetMapping
    @Operation(summary = "오늘의 카테고리별 추천 조회", description = "채용공고/대외활동을 카테고리별로 추천합니다.")
    fun getRecommendations(): ResponseEntity<CategoryRecommendationResponse> =
        ResponseEntity.ok(recommendationService.getRecommendations())
}
