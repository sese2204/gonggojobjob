package org.example.kotlinai.service

import org.example.kotlinai.dto.request.JobSearchRequest
import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.dto.response.JobResult
import org.example.kotlinai.dto.response.JobSearchResponse
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.repository.JobListingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class JobSearchService(
    private val jobListingRepository: JobListingRepository,
    private val geminiService: GeminiService,
) {

    fun search(request: JobSearchRequest): JobSearchResponse {
        require(request.tags.isNotEmpty() || request.query.isNotBlank()) {
            "tags 또는 query 중 하나는 반드시 입력해야 합니다."
        }

        val listings = jobListingRepository.findTop10ByOrderByCollectedAtDesc()
        val totalCount = jobListingRepository.count().toInt()
        val today = LocalDate.now()
        val newTodayCount = listings.count { it.collectedAt.toLocalDate() == today }

        if (listings.isEmpty()) {
            return JobSearchResponse(jobs = emptyList(), totalCount = 0, newTodayCount = 0)
        }

        val summaries = listings.map { it.toAiSummary() }
        val aiResults = geminiService.matchJobs(request.tags, request.query, summaries)

        val aiResultMap = aiResults.associateBy { it.id }

        val jobs = listings
            .map { listing ->
                val aiResult = aiResultMap[listing.id.toString()]
                listing.toResult(aiResult)
            }
            .sortedByDescending { it.match }

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
