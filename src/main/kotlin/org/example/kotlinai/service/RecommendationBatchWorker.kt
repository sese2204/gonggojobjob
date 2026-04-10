package org.example.kotlinai.service

import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.entity.ActivityListing
import org.example.kotlinai.entity.DailyRecommendation
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.entity.RecommendationCategory
import org.example.kotlinai.repository.DailyRecommendationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class RecommendationBatchWorker(
    private val hybridSearchService: HybridSearchService,
    private val activityHybridSearchService: ActivityHybridSearchService,
    private val geminiService: GeminiService,
    private val dailyRecommendationRepository: DailyRecommendationRepository,
) {
    private val log = LoggerFactory.getLogger(RecommendationBatchWorker::class.java)

    @Transactional
    fun deleteExisting(date: LocalDate) {
        dailyRecommendationRepository.deleteByGeneratedAt(date)
    }

    @Transactional
    fun generateJobRecommendations(category: RecommendationCategory, limit: Int, date: LocalDate) {
        val candidateCount = limit * 2
        val listings = hybridSearchService.search(category.searchTags, "", candidateCount)
        if (listings.isEmpty()) {
            log.info("[Recommendation] {} — 검색 결과 없음", category.name)
            return
        }

        val summaries = listings.map { it.toAiJobSummary() }
        val aiResultMap = try {
            geminiService.matchJobs(category.searchTags, category.displayName, summaries)
                .associateBy { it.id }
        } catch (e: Exception) {
            log.warn("[Recommendation] {} AI 매칭 실패: {}", category.name, e.message)
            emptyMap()
        }

        val recommendations = listings.map { listing ->
            val aiResult = aiResultMap[listing.id.toString()]
            listing.toDailyRecommendation(category, aiResult, date)
        }
            .sortedByDescending { it.matchScore }
            .take(limit)

        dailyRecommendationRepository.saveAll(recommendations)
        log.info("[Recommendation] {} — {}건 저장", category.displayName, recommendations.size)
    }

    @Transactional
    fun generateActivityRecommendations(category: RecommendationCategory, limit: Int, date: LocalDate) {
        val candidateCount = limit * 2
        val listings = activityHybridSearchService.search(category.searchTags, "", candidateCount)
        if (listings.isEmpty()) {
            log.info("[Recommendation] {} — 검색 결과 없음", category.name)
            return
        }

        val summaries = listings.map { it.toAiActivitySummary() }
        val aiResultMap = try {
            geminiService.matchActivities(category.searchTags, category.displayName, summaries)
                .associateBy { it.id }
        } catch (e: Exception) {
            log.warn("[Recommendation] {} AI 매칭 실패: {}", category.name, e.message)
            emptyMap()
        }

        val recommendations = listings.map { listing ->
            val aiResult = aiResultMap[listing.id.toString()]
            listing.toDailyRecommendation(category, aiResult, date)
        }
            .sortedByDescending { it.matchScore }
            .take(limit)

        dailyRecommendationRepository.saveAll(recommendations)
        log.info("[Recommendation] {} — {}건 저장", category.displayName, recommendations.size)
    }
}

private fun JobListing.toAiJobSummary() = AiJobSummary(
    id = id.toString(),
    title = title,
    company = company,
    description = description,
)

private fun ActivityListing.toAiActivitySummary() = AiActivitySummary(
    id = id.toString(),
    title = title,
    organizer = organizer,
    category = category,
    description = description,
)

private fun JobListing.toDailyRecommendation(
    category: RecommendationCategory,
    aiResult: AiMatchResult?,
    date: LocalDate,
) = DailyRecommendation(
    category = category,
    jobListing = this,
    title = title,
    companyOrOrganizer = company,
    url = url,
    matchScore = aiResult?.match?.coerceIn(0, 100) ?: 0,
    reason = aiResult?.reason?.takeIf { it.isNotBlank() } ?: "AI 분석 결과 관련 공고입니다.",
    generatedAt = date,
)

private fun ActivityListing.toDailyRecommendation(
    category: RecommendationCategory,
    aiResult: AiMatchResult?,
    date: LocalDate,
) = DailyRecommendation(
    category = category,
    activityListing = this,
    title = title,
    companyOrOrganizer = organizer,
    url = url,
    activityCategory = this.category,
    startDate = startDate,
    endDate = endDate,
    matchScore = aiResult?.match?.coerceIn(0, 100) ?: 0,
    reason = aiResult?.reason?.takeIf { it.isNotBlank() } ?: "AI 분석 결과 관련 활동입니다.",
    generatedAt = date,
)
