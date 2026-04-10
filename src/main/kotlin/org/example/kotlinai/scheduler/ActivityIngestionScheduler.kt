package org.example.kotlinai.scheduler

import org.example.kotlinai.service.ActivityIngestionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ActivityIngestionScheduler(
    private val activityIngestionService: ActivityIngestionService,
) {
    private val log = LoggerFactory.getLogger(ActivityIngestionScheduler::class.java)

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
    fun dailyIngestion() {
        log.info("[Activity Scheduler] 일일 공모전/대외활동 수집 시작")
        try {
            val results = activityIngestionService.runIngestion(null)
            val totalNew = results.sumOf { it.newCount }
            val totalDuplicate = results.sumOf { it.duplicateCount }
            log.info("[Activity Scheduler] 일일 수집 완료 — 신규: {}건, 중복: {}건", totalNew, totalDuplicate)
        } catch (e: Exception) {
            log.error("[Activity Scheduler] 일일 수집 실패: {}", e.message, e)
        }
    }
}
