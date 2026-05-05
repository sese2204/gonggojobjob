package org.example.kotlinai.scheduler

import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.repository.DailyRecommendationRepository
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
    private val dailyRecommendationRepository: DailyRecommendationRepository,
) {
    private val log = LoggerFactory.getLogger(ExpiredListingCleanupScheduler::class.java)

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    @Transactional
    fun cleanupExpiredListings() {
        val today = LocalDate.now()
        val listingCutoff = today.minusDays(GRACE_PERIOD_DAYS)
        val recommendationCutoff = today.minusDays(RECOMMENDATION_RETENTION_DAYS)

        log.info("[Cleanup] 만료 공고 정리 시작 (공고 기준일: {}, 추천 기준일: {})", listingCutoff, recommendationCutoff)

        val deletedJobs = jobListingRepository.deleteExpired(listingCutoff)
        val deletedActivities = activityListingRepository.deleteExpired(listingCutoff)
        val deletedRecommendations = dailyRecommendationRepository.deleteByGeneratedAtBefore(recommendationCutoff)

        log.info(
            "[Cleanup] 정리 완료 — 채용: {}건, 활동: {}건, 추천: {}건 삭제",
            deletedJobs, deletedActivities, deletedRecommendations,
        )
    }

    companion object {
        private const val GRACE_PERIOD_DAYS = 7L
        private const val RECOMMENDATION_RETENTION_DAYS = 7L
    }
}
