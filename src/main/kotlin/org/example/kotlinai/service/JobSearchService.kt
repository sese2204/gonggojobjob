package org.example.kotlinai.service

import org.example.kotlinai.config.RagProperties
import org.example.kotlinai.global.exception.DailySearchLimitExceededException
import org.example.kotlinai.dto.request.JobSearchRequest
import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.dto.response.JobResult
import org.example.kotlinai.dto.response.JobSearchResponse
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.entity.RecommendedJob
import org.example.kotlinai.entity.SearchHistory
import org.example.kotlinai.repository.JobListingRepository
import org.example.kotlinai.repository.SearchHistoryRepository
import org.example.kotlinai.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class JobSearchService(
    private val jobListingRepository: JobListingRepository,
    private val geminiService: GeminiService,
    private val hybridSearchService: HybridSearchService,
    private val ragProperties: RagProperties,
    private val userRepository: UserRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val searchCacheService: SearchCacheService,
) {
    private val log = LoggerFactory.getLogger(JobSearchService::class.java)

    @Transactional
    fun search(request: JobSearchRequest): JobSearchResponse {
        require(request.tags.isNotEmpty() || request.query.isNotBlank()) {
            "tags 또는 query 중 하나는 반드시 입력해야 합니다."
        }
        require(request.query.length <= 500) {
            "검색 쿼리는 500자를 초과할 수 없습니다."
        }

        val userId = currentUserId()
        val cacheKey = searchCacheService.buildKey(userId, "job", request.tags, request.query)
        val cached = searchCacheService.get<JobSearchResponse>(cacheKey)
        if (cached != null) {
            log.info("[Search] 캐시 히트 — tags={}, query='{}'", request.tags, request.query)
            return cached
        }

        if (userId != null) {
            val todayCount = searchHistoryRepository.countByUserIdAndSearchedAtAfter(
                userId, LocalDate.now().atStartOfDay()
            )
            if (todayCount >= DAILY_SEARCH_LIMIT) {
                throw DailySearchLimitExceededException()
            }
        }

        log.info("[Search] 요청 - tags={}, query='{}'", request.tags, request.query)

        val startMs = System.currentTimeMillis()
        val totalCount = jobListingRepository.count().toInt()
        val today = LocalDate.now()

        val listings = hybridSearchService.search(request.tags, request.query, ragProperties.topN)
        log.info("[Search] 하이브리드 검색 결과: {}건", listings.size)

        if (listings.isEmpty()) {
            log.warn("[Search] 하이브리드 검색 결과 0건 → 빈 응답 반환")
            val latencyMs = System.currentTimeMillis() - startMs
            val response = JobSearchResponse(jobs = emptyList(), totalCount = totalCount, newTodayCount = 0)
            saveSearchHistory(request, emptyList(), emptyMap(), userId, listings.size, null, latencyMs)
            searchCacheService.put(cacheKey, response)
            return response
        }

        val newTodayCount = listings.count { it.collectedAt.toLocalDate() == today }

        val summaries = listings.map { it.toAiSummary() }
        val (aiResultMap, geminiInputChars) = try {
            val response = geminiService.matchJobs(request.tags, request.query, summaries)
            response.results.associateBy { it.id } to response.inputChars
        } catch (e: Exception) {
            log.warn("[Search] AI 매칭 실패, 점수 없이 반환: {}", e.message)
            emptyMap<String, AiMatchResult>() to null
        }

        val latencyMs = System.currentTimeMillis() - startMs
        log.info("[Search] latency={}ms geminiInputChars={}", latencyMs, geminiInputChars)

        val jobs = listings
            .map { listing ->
                val aiResult = aiResultMap[listing.id.toString()]
                listing.toResult(aiResult)
            }
            .sortedByDescending { it.match }

        log.info("[Search] 최종 응답 - {}건, totalCount={}, newToday={}", jobs.size, totalCount, newTodayCount)

        saveSearchHistory(request, listings, aiResultMap, userId, listings.size, geminiInputChars, latencyMs)

        val response = JobSearchResponse(jobs = jobs, totalCount = totalCount, newTodayCount = newTodayCount)
        searchCacheService.put(cacheKey, response)
        return response
    }

    companion object {
        private const val DAILY_SEARCH_LIMIT = 15
    }

    private fun currentUserId(): Long? {
        val auth = SecurityContextHolder.getContext().authentication
        log.debug("[Search] SecurityContext auth={}, principalType={}",
            auth?.javaClass?.simpleName, auth?.principal?.javaClass?.simpleName)
        return auth?.principal as? Long
    }

    private fun saveSearchHistory(
        request: JobSearchRequest,
        listings: List<JobListing>,
        aiResultMap: Map<String, AiMatchResult>,
        userId: Long?,
        hybridResultCount: Int,
        geminiInputChars: Int?,
        latencyMs: Long,
    ) {
        if (userId == null) {
            log.warn("[Search] 인증된 사용자 없음 — 검색 기록 저장 스킵 (토큰 미포함 또는 만료)")
            return
        }

        val user = userRepository.findById(userId).orElse(null)
        if (user == null) {
            log.warn("[Search] userId={}에 해당하는 사용자를 찾을 수 없어 검색 기록 저장을 건너뜁니다.", userId)
            return
        }

        val searchHistory = SearchHistory(
            user = user,
            tagsString = request.tags.takeIf { it.isNotEmpty() }?.joinToString(","),
            query = request.query.takeIf { it.isNotBlank() },
            resultCount = listings.size,
            hybridResultCount = hybridResultCount,
            geminiInputChars = geminiInputChars,
            latencyMs = latencyMs,
        )

        listings.forEach { listing ->
            val aiResult = aiResultMap[listing.id.toString()]
            val match = aiResult?.match?.coerceIn(0, 100) ?: 0
            val reason = aiResult?.reason?.takeIf { it.isNotBlank() } ?: "AI 분석 결과 관련 공고입니다."

            searchHistory.recommendedJobs.add(
                RecommendedJob(
                    searchHistory = searchHistory,
                    jobListing = listing,
                    title = listing.title,
                    company = listing.company,
                    url = listing.url,
                    matchScore = match,
                    reason = reason,
                )
            )
        }

        searchHistoryRepository.save(searchHistory)
        log.info("[Search] 검색 기록 저장 완료 - userId={}, historyId={}, 추천공고={}건",
            userId, searchHistory.id, listings.size)
    }
}

private fun JobListing.toAiSummary() = AiJobSummary(
    id = id.toString(),
    title = title,
    company = company,
    description = description,
)

private fun JobListing.toResult(aiResult: AiMatchResult?): JobResult {
    val match = aiResult?.match?.coerceIn(0, 100) ?: 0
    val reason = aiResult?.reason?.takeIf { it.isNotBlank() } ?: "AI 분석 결과 관련 공고입니다."
    return JobResult(
        id = id.toString(),
        title = title,
        company = company,
        match = match,
        reason = reason,
        url = url,
    )
}
