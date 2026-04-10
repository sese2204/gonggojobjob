package org.example.kotlinai.service

import org.example.kotlinai.config.RecommendationProperties
import org.example.kotlinai.entity.RecommendationCategory
import org.example.kotlinai.repository.DailyRecommendationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

@Service
class RecommendationBatchService(
    private val worker: RecommendationBatchWorker,
    private val dailyRecommendationRepository: DailyRecommendationRepository,
    private val recommendationProperties: RecommendationProperties,
) {
    private val log = LoggerFactory.getLogger(RecommendationBatchService::class.java)
    private val isGenerating = AtomicBoolean(false)

    fun generateIfEmpty() {
        val today = LocalDate.now(ZONE_SEOUL)
        val count = dailyRecommendationRepository.countByGeneratedAt(today)
        if (count > 0) {
            log.info("[Recommendation] 오늘({}) 추천 {}건 이미 존재 — 스킵", today, count)
            return
        }
        log.info("[Recommendation] 오늘({}) 추천 없음 — 생성 시작", today)
        generateDailyRecommendations()
    }

    fun generateDailyRecommendations() {
        if (!isGenerating.compareAndSet(false, true)) {
            log.warn("[Recommendation] 이미 생성 중입니다. 요청 무시.")
            return
        }

        try {
            val today = LocalDate.now(ZONE_SEOUL)
            log.info("[Recommendation] 일일 추천 생성 시작 ({})", today)

            worker.deleteExisting(today)

            val limit = recommendationProperties.itemsPerCategory

            RecommendationCategory.jobCategories.forEach { category ->
                try {
                    worker.generateJobRecommendations(category, limit, today)
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    log.warn("[Recommendation] {} 카테고리 생성 실패: {}", category.name, e.message)
                }
            }

            RecommendationCategory.activityCategories.forEach { category ->
                try {
                    worker.generateActivityRecommendations(category, limit, today)
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    log.warn("[Recommendation] {} 카테고리 생성 실패: {}", category.name, e.message)
                }
            }

            val totalCount = dailyRecommendationRepository.countByGeneratedAt(today)
            log.info("[Recommendation] 일일 추천 생성 완료 — {}건", totalCount)
        } finally {
            isGenerating.set(false)
        }
    }

    companion object {
        val ZONE_SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
