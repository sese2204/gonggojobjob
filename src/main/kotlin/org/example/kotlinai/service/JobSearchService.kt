package org.example.kotlinai.service

import org.example.kotlinai.config.RagProperties
import org.example.kotlinai.dto.request.JobSearchRequest
import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.dto.response.JobResult
import org.example.kotlinai.dto.response.JobSearchResponse
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.repository.JobListingRepository
import org.slf4j.LoggerFactory
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
) {
    private val log = LoggerFactory.getLogger(JobSearchService::class.java)

    fun search(request: JobSearchRequest): JobSearchResponse {
        require(request.tags.isNotEmpty() || request.query.isNotBlank()) {
            "tags 또는 query 중 하나는 반드시 입력해야 합니다."
        }

        log.info("[Search] 요청 - tags={}, query='{}'", request.tags, request.query)

        val totalCount = jobListingRepository.count().toInt()
        log.info("[Search] DB 전체 공고 수: {}", totalCount)

        val today = LocalDate.now()

        val listings = hybridSearchService.search(request.tags, request.query, ragProperties.topN)
        log.info("[Search] 하이브리드 검색 결과: {}건", listings.size)

        if (listings.isEmpty()) {
            log.warn("[Search] 하이브리드 검색 결과 0건 → 빈 응답 반환")
            return JobSearchResponse(jobs = emptyList(), totalCount = totalCount, newTodayCount = 0)
        }

        val newTodayCount = listings.count { it.collectedAt.toLocalDate() == today }

        val summaries = listings.map { it.toAiSummary() }
        log.info("[Search] AI 매칭 요청 - {}건 공고", summaries.size)
        val aiResults = geminiService.matchJobs(request.tags, request.query, summaries)
        log.info("[Search] AI 매칭 응답 - {}건 결과", aiResults.size)

        val aiResultMap = aiResults.associateBy { it.id }

        val jobs = listings
            .map { listing ->
                val aiResult = aiResultMap[listing.id.toString()]
                listing.toResult(aiResult)
            }
            .sortedByDescending { it.match }

        log.info("[Search] 최종 응답 - {}건, totalCount={}, newToday={}", jobs.size, totalCount, newTodayCount)
        return JobSearchResponse(jobs = jobs, totalCount = totalCount, newTodayCount = newTodayCount)
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
