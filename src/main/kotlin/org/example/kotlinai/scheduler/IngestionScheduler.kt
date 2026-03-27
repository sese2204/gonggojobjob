package org.example.kotlinai.scheduler

import org.example.kotlinai.service.JobIngestionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class IngestionScheduler(
    private val jobIngestionService: JobIngestionService,
) {
    private val log = LoggerFactory.getLogger(IngestionScheduler::class.java)

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    fun dailyIngestion() {
        log.info("[Scheduler] 일일 공고 수집 시작")
        try {
            val results = jobIngestionService.runIngestion(null)
            val totalNew = results.sumOf { it.newCount }
            val totalDuplicate = results.sumOf { it.duplicateCount }
            log.info("[Scheduler] 일일 공고 수집 완료 — 신규: {}건, 중복: {}건", totalNew, totalDuplicate)
        } catch (e: Exception) {
            log.error("[Scheduler] 일일 공고 수집 실패: {}", e.message, e)
        }
    }
}
