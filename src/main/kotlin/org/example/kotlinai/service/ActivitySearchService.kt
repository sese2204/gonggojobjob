package org.example.kotlinai.service

import org.example.kotlinai.config.RagProperties
import org.example.kotlinai.dto.request.ActivitySearchRequest
import org.example.kotlinai.dto.response.ActivityResult
import org.example.kotlinai.dto.response.ActivitySearchResponse
import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.entity.ActivityListing
import org.example.kotlinai.entity.RecommendedActivity
import org.example.kotlinai.entity.ActivitySearchHistory
import org.example.kotlinai.global.exception.DailySearchLimitExceededException
import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.repository.ActivitySearchHistoryRepository
import org.example.kotlinai.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ActivitySearchService(
    private val activityListingRepository: ActivityListingRepository,
    private val geminiService: GeminiService,
    private val activityHybridSearchService: ActivityHybridSearchService,
    private val ragProperties: RagProperties,
    private val userRepository: UserRepository,
    private val activitySearchHistoryRepository: ActivitySearchHistoryRepository,
    private val searchCacheService: SearchCacheService,
) {
    private val log = LoggerFactory.getLogger(ActivitySearchService::class.java)

    @Transactional
    fun search(request: ActivitySearchRequest): ActivitySearchResponse {
        require(request.tags.isNotEmpty() || request.query.isNotBlank()) {
            "tags 또는 query 중 하나는 반드시 입력해야 합니다."
        }
        require(request.query.length <= 500) {
            "검색 쿼리는 500자를 초과할 수 없습니다."
        }

        val userId = currentUserId()
        val cacheKey = searchCacheService.buildKey(userId, "activity", request.tags, request.query)
        val cached = searchCacheService.get<ActivitySearchResponse>(cacheKey)
        if (cached != null) {
            log.info("[ActivitySearch] 캐시 히트 — tags={}, query='{}'", request.tags, request.query)
            return cached
        }

        if (userId != null) {
            val todayCount = activitySearchHistoryRepository.countByUserIdAndSearchedAtAfter(
                userId, LocalDate.now().atStartOfDay(),
            )
            if (todayCount >= DAILY_SEARCH_LIMIT) {
                throw DailySearchLimitExceededException()
            }
        }

        log.info("[ActivitySearch] tags={}, query='{}'", request.tags, request.query)

        val totalCount = activityListingRepository.count().toInt()
        val today = LocalDate.now()

        val listings = activityHybridSearchService.search(request.tags, request.query, ragProperties.topN)
        log.info("[ActivitySearch] hybrid search results: {}", listings.size)

        if (listings.isEmpty()) {
            saveSearchHistory(request, emptyList(), emptyMap(), userId)
            val response = ActivitySearchResponse(activities = emptyList(), totalCount = totalCount, newTodayCount = 0)
            searchCacheService.put(cacheKey, response)
            return response
        }

        val newTodayCount = listings.count { it.collectedAt.toLocalDate() == today }

        val summaries = listings.map { it.toAiSummary() }
        val aiResultMap = try {
            geminiService.matchActivities(request.tags, request.query, summaries).associateBy { it.id }
        } catch (e: Exception) {
            log.warn("[ActivitySearch] AI matching failed: {}", e.message)
            emptyMap()
        }

        val activities = listings
            .map { listing ->
                val aiResult = aiResultMap[listing.id.toString()]
                listing.toResult(aiResult)
            }
            .sortedByDescending { it.match }

        saveSearchHistory(request, listings, aiResultMap, userId)

        val response = ActivitySearchResponse(activities = activities, totalCount = totalCount, newTodayCount = newTodayCount)
        searchCacheService.put(cacheKey, response)
        return response
    }

    companion object {
        private const val DAILY_SEARCH_LIMIT = 15
    }

    private fun currentUserId(): Long? {
        val auth = SecurityContextHolder.getContext().authentication
        return auth?.principal as? Long
    }

    private fun saveSearchHistory(
        request: ActivitySearchRequest,
        listings: List<ActivityListing>,
        aiResultMap: Map<String, AiMatchResult>,
        userId: Long?,
    ) {
        if (userId == null) {
            log.warn("[ActivitySearch] no authenticated user — skipping history save")
            return
        }

        val user = userRepository.findById(userId).orElse(null)
        if (user == null) {
            log.warn("[ActivitySearch] userId={} not found — skipping history save", userId)
            return
        }

        val searchHistory = ActivitySearchHistory(
            user = user,
            tagsString = request.tags.takeIf { it.isNotEmpty() }?.joinToString(","),
            query = request.query.takeIf { it.isNotBlank() },
            resultCount = listings.size,
        )

        listings.forEach { listing ->
            val aiResult = aiResultMap[listing.id.toString()]
            val match = aiResult?.match?.coerceIn(0, 100) ?: 0
            val reason = aiResult?.reason?.takeIf { it.isNotBlank() } ?: "AI 분석 결과 관련 활동입니다."

            searchHistory.recommendedActivities.add(
                RecommendedActivity(
                    activitySearchHistory = searchHistory,
                    activityListing = listing,
                    title = listing.title,
                    organizer = listing.organizer,
                    url = listing.url,
                    category = listing.category,
                    startDate = listing.startDate,
                    endDate = listing.endDate,
                    matchScore = match,
                    reason = reason,
                ),
            )
        }

        activitySearchHistoryRepository.save(searchHistory)
        log.info("[ActivitySearch] history saved — userId={}, historyId={}, recommendations={}",
            userId, searchHistory.id, listings.size)
    }
}

private fun ActivityListing.toAiSummary() = AiActivitySummary(
    id = id.toString(),
    title = title,
    organizer = organizer,
    category = category,
    description = description,
)

private fun ActivityListing.toResult(aiResult: AiMatchResult?): ActivityResult {
    val match = aiResult?.match?.coerceIn(0, 100) ?: 0
    val reason = aiResult?.reason?.takeIf { it.isNotBlank() } ?: "AI 분석 결과 관련 활동입니다."
    return ActivityResult(
        id = id.toString(),
        title = title,
        organizer = organizer,
        category = category,
        startDate = startDate,
        endDate = endDate,
        description = description,
        url = url,
        match = match,
        reason = reason,
    )
}
