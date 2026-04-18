package org.example.kotlinai.scheduler

import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.repository.JobListingRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class ExpiredListingCleanupScheduler(
    private val jobListingRepository: JobListingRepository,
    private val activityListingRepository: ActivityListingRepository,
) {
    private val log = LoggerFactory.getLogger(ExpiredListingCleanupScheduler::class.java)

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    @Transactional
    fun cleanupExpiredListings() {
        val cutoff = LocalDate.now().minusDays(GRACE_PERIOD_DAYS)
        log.info("[Cleanup] 만료 공고 정리 시작 (기준일: {})", cutoff)

        val deletedJobs = jobListingRepository.deleteExpired(cutoff)
        val deletedActivities = activityListingRepository.deleteExpired(cutoff)

        log.info("[Cleanup] 만료 공고 정리 완료 — 채용: {}건, 활동: {}건 삭제", deletedJobs, deletedActivities)
    }

    companion object {
        private const val GRACE_PERIOD_DAYS = 7L
    }
}
