package org.example.kotlinai.scheduler

import org.example.kotlinai.service.RecommendationBatchService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RecommendationScheduler(
    private val recommendationBatchService: RecommendationBatchService,
) {
    private val log = LoggerFactory.getLogger(RecommendationScheduler::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Async
    fun onStartup() {
        log.info("[Scheduler] 서버 시작 — 일일 추천 생성 확인")
        try {
            recommendationBatchService.generateIfEmpty()
        } catch (e: Exception) {
            log.error("[Scheduler] 시작 시 추천 생성 실패: {}", e.message, e)
        }
    }

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
