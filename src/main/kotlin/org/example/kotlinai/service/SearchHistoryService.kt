package org.example.kotlinai.service

import org.example.kotlinai.dto.response.RecommendedJobResponse
import org.example.kotlinai.dto.response.SearchHistoryDetailResponse
import org.example.kotlinai.dto.response.SearchHistoryResponse
import org.example.kotlinai.entity.RecommendedJob
import org.example.kotlinai.entity.SearchHistory
import org.example.kotlinai.repository.RecommendedJobRepository
import org.example.kotlinai.repository.SearchHistoryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SearchHistoryService(
    private val searchHistoryRepository: SearchHistoryRepository,
    private val recommendedJobRepository: RecommendedJobRepository,
) {

    fun getSearchHistories(userId: Long, pageable: Pageable): Page<SearchHistoryResponse> =
        searchHistoryRepository.findAllByUserIdOrderBySearchedAtDesc(userId, pageable)
            .map { it.toResponse() }

    fun getSearchDetail(searchId: Long): SearchHistoryDetailResponse =
        searchHistoryRepository.findById(searchId)
            .orElseThrow { NoSuchElementException("검색 기록을 찾을 수 없습니다. id=$searchId") }
            .toDetailResponse()

    fun getRecommendedJobs(userId: Long, pageable: Pageable): Page<RecommendedJobResponse> =
        recommendedJobRepository.findAllByUserId(userId, pageable)
            .map { it.toResponse() }

    @Transactional
    fun deleteRecommendedJob(userId: Long, recommendedJobId: Long) {
        val job = recommendedJobRepository.findByIdAndSearchHistoryUserId(recommendedJobId, userId)
            ?: throw NoSuchElementException("추천 공고를 찾을 수 없습니다. id=$recommendedJobId")
        recommendedJobRepository.delete(job)
    }
}

private fun SearchHistory.toResponse() = SearchHistoryResponse(
    id = id,
    tags = tags,
    query = query,
    resultCount = resultCount,
    searchedAt = searchedAt,
)

private fun SearchHistory.toDetailResponse() = SearchHistoryDetailResponse(
    id = id,
    tags = tags,
    query = query,
    resultCount = resultCount,
    searchedAt = searchedAt,
    recommendedJobs = recommendedJobs.map { it.toResponse() },
)

private fun RecommendedJob.toResponse() = RecommendedJobResponse(
    id = id,
    jobListingId = jobListing.id,
    title = title,
    company = company,
    url = url,
    matchScore = matchScore,
    reason = reason,
    searchedAt = searchHistory.searchedAt,
)
