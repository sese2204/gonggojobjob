package org.example.kotlinai.service

import org.example.kotlinai.dto.response.ActivitySearchHistoryDetailResponse
import org.example.kotlinai.dto.response.ActivitySearchHistoryResponse
import org.example.kotlinai.dto.response.RecommendedActivityResponse
import org.example.kotlinai.entity.ActivitySearchHistory
import org.example.kotlinai.entity.RecommendedActivity
import org.example.kotlinai.repository.ActivitySearchHistoryRepository
import org.example.kotlinai.repository.RecommendedActivityRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ActivitySearchHistoryService(
    private val activitySearchHistoryRepository: ActivitySearchHistoryRepository,
    private val recommendedActivityRepository: RecommendedActivityRepository,
) {

    fun getSearchHistories(userId: Long, pageable: Pageable): Page<ActivitySearchHistoryResponse> =
        activitySearchHistoryRepository.findAllByUserIdOrderBySearchedAtDesc(userId, pageable)
            .map { it.toResponse() }

    fun getSearchDetail(searchId: Long): ActivitySearchHistoryDetailResponse =
        activitySearchHistoryRepository.findById(searchId)
            .orElseThrow { NoSuchElementException("활동 검색 기록을 찾을 수 없습니다. id=$searchId") }
            .toDetailResponse()

    fun getRecommendedActivities(userId: Long, pageable: Pageable): Page<RecommendedActivityResponse> =
        recommendedActivityRepository.findAllByUserId(userId, pageable)
            .map { it.toResponse() }

    @Transactional
    fun deleteRecommendedActivity(userId: Long, recommendedActivityId: Long) {
        val activity = recommendedActivityRepository.findByIdAndActivitySearchHistoryUserId(recommendedActivityId, userId)
            ?: throw NoSuchElementException("추천 활동을 찾을 수 없습니다. id=$recommendedActivityId")
        recommendedActivityRepository.delete(activity)
    }
}

private fun ActivitySearchHistory.toResponse() = ActivitySearchHistoryResponse(
    id = id,
    tags = tags,
    query = query,
    resultCount = resultCount,
    searchedAt = searchedAt,
)

private fun ActivitySearchHistory.toDetailResponse() = ActivitySearchHistoryDetailResponse(
    id = id,
    tags = tags,
    query = query,
    resultCount = resultCount,
    searchedAt = searchedAt,
    recommendedActivities = recommendedActivities.map { it.toResponse() },
)

private fun RecommendedActivity.toResponse() = RecommendedActivityResponse(
    id = id,
    activityListingId = activityListing.id,
    title = title,
    organizer = organizer,
    url = url,
    category = category,
    startDate = startDate,
    endDate = endDate,
    matchScore = matchScore,
    reason = reason,
    searchedAt = activitySearchHistory.searchedAt,
)
