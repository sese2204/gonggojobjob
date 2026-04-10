package org.example.kotlinai.service

import org.example.kotlinai.dto.response.*
import org.example.kotlinai.entity.DailyRecommendation
import org.example.kotlinai.entity.RecommendationCategory
import org.example.kotlinai.repository.DailyRecommendationRepository
import org.example.kotlinai.service.RecommendationBatchService.Companion.ZONE_SEOUL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class RecommendationService(
    private val dailyRecommendationRepository: DailyRecommendationRepository,
) {
    private val log = LoggerFactory.getLogger(RecommendationService::class.java)

    fun getRecommendations(): CategoryRecommendationResponse {
        val targetDate = resolveTargetDate()
        if (targetDate == null) {
            log.info("[Recommendation] 추천 데이터 없음")
            return emptyResponse()
        }

        val recommendations = dailyRecommendationRepository
            .findByGeneratedAtOrderByCategoryAscMatchScoreDesc(targetDate)

        val grouped = recommendations.groupBy { it.category }

        val jobCategories = RecommendationCategory.jobCategories.mapNotNull { category ->
            val items = grouped[category] ?: return@mapNotNull null
            JobCategoryGroup(
                category = category.name,
                displayName = category.displayName,
                jobs = items.map { it.toJobItem() },
            )
        }

        val activityCategories = RecommendationCategory.activityCategories.mapNotNull { category ->
            val items = grouped[category] ?: return@mapNotNull null
            ActivityCategoryGroup(
                category = category.name,
                displayName = category.displayName,
                activities = items.map { it.toActivityItem() },
            )
        }

        return CategoryRecommendationResponse(
            jobCategories = jobCategories,
            activityCategories = activityCategories,
            generatedAt = targetDate,
        )
    }

    private fun resolveTargetDate(): LocalDate? {
        val today = LocalDate.now(ZONE_SEOUL)
        val todayCount = dailyRecommendationRepository.countByGeneratedAt(today)
        if (todayCount > 0) return today

        return dailyRecommendationRepository.findLatestGeneratedAt()
    }

    private fun emptyResponse() = CategoryRecommendationResponse(
        jobCategories = emptyList(),
        activityCategories = emptyList(),
        generatedAt = null,
    )
}

private fun DailyRecommendation.toJobItem() = JobRecommendationItem(
    jobListingId = requireNotNull(jobListingId) { "DailyRecommendation(id=$id)에 jobListingId가 없습니다." },
    title = title,
    company = companyOrOrganizer ?: "",
    url = url,
    matchScore = matchScore,
    reason = reason,
)

private fun DailyRecommendation.toActivityItem() = ActivityRecommendationItem(
    activityListingId = requireNotNull(activityListingId) { "DailyRecommendation(id=$id)에 activityListingId가 없습니다." },
    title = title,
    organizer = companyOrOrganizer ?: "",
    category = activityCategory,
    startDate = startDate,
    endDate = endDate,
    url = url,
    matchScore = matchScore,
    reason = reason,
)
