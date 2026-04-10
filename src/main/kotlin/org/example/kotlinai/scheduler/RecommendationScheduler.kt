package org.example.kotlinai.scheduler

import org.example.kotlinai.service.RecommendationBatchService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RecommendationScheduler(
    private val recommendationBatchService: RecommendationBatchService,
) {
    private val log = LoggerFactory.getLogger(RecommendationScheduler::class.java)

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    fun dailyRecommendation() {
        log.info("[Scheduler] 일일 추천 생성 시작")
        try {
            recommendationBatchService.generateDailyRecommendations()
            log.info("[Scheduler] 일일 추천 생성 완료")
        } catch (e: Exception) {
            log.error("[Scheduler] 일일 추천 생성 실패: {}", e.message, e)
        }
    }
}
