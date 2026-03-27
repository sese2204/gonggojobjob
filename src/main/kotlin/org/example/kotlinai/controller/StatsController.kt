package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.response.StatsResponse
import org.example.kotlinai.service.StatsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Stats", description = "공고 통계 API")
@RestController
@RequestMapping("/api/stats")
class StatsController(
    private val statsService: StatsService,
) {

    @Operation(
        summary = "공고 통계 조회",
        description = "누적 공고 수와 오늘 새로 수집된 공고 수를 반환합니다.",
    )
    @GetMapping
    fun getStats(): StatsResponse = statsService.getStats()
}
