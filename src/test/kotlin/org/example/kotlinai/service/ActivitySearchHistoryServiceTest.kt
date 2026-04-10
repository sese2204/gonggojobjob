package org.example.kotlinai.service

import org.example.kotlinai.entity.ActivityListing
import org.example.kotlinai.entity.ActivitySearchHistory
import org.example.kotlinai.entity.RecommendedActivity
import org.example.kotlinai.entity.User
import org.example.kotlinai.repository.ActivitySearchHistoryRepository
import org.example.kotlinai.repository.RecommendedActivityRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional
import kotlin.test.assertEquals

class ActivitySearchHistoryServiceTest {

    private lateinit var activitySearchHistoryRepository: ActivitySearchHistoryRepository
    private lateinit var recommendedActivityRepository: RecommendedActivityRepository
    private lateinit var service: ActivitySearchHistoryService

    private val user = User("test@test.com", "테스트")
    private val pageable = PageRequest.of(0, 20)
    private val activityListing = ActivityListing(
        title = "AI 공모전", organizer = "과기부", url = "https://contest.kr/1",
        category = "IT/SW", sourceName = "allcon", sourceId = "1",
    )

    @BeforeEach
    fun setUp() {
        activitySearchHistoryRepository = mock()
        recommendedActivityRepository = mock()
        service = ActivitySearchHistoryService(activitySearchHistoryRepository, recommendedActivityRepository)
    }

    @Test
    fun `getSearchHistories returns paginated list`() {
        val history = ActivitySearchHistory(user, "IT", "공모전", 5)
        whenever(activitySearchHistoryRepository.findAllByUserIdOrderBySearchedAtDesc(1L, pageable))
            .thenReturn(PageImpl(listOf(history), pageable, 1))

        val result = service.getSearchHistories(1L, pageable)

        assertEquals(1, result.totalElements)
        assertEquals(listOf("IT"), result.content[0].tags)
        assertEquals("공모전", result.content[0].query)
        assertEquals(5, result.content[0].resultCount)
    }

    @Test
    fun `getSearchDetail returns history with recommended activities`() {
        val history = ActivitySearchHistory(user, "IT", "공모전", 1)
        val recommended = RecommendedActivity(
            activitySearchHistory = history,
            activityListing = activityListing,
            title = "AI 공모전",
            organizer = "과기부",
            url = "https://contest.kr/1",
            category = "IT/SW",
            matchScore = 90,
            reason = "IT 분야 공모전",
        )
        history.recommendedActivities.add(recommended)
        whenever(activitySearchHistoryRepository.findById(1L)).thenReturn(Optional.of(history))

        val result = service.getSearchDetail(1L)

        assertEquals(1, result.recommendedActivities.size)
        assertEquals("AI 공모전", result.recommendedActivities[0].title)
        assertEquals(90, result.recommendedActivities[0].matchScore)
    }

    @Test
    fun `getSearchDetail throws for invalid id`() {
        whenever(activitySearchHistoryRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> {
            service.getSearchDetail(999L)
        }
    }

    @Test
    fun `getRecommendedActivities returns paginated list`() {
        val history = ActivitySearchHistory(user, "IT", null, 1)
        val recommended = RecommendedActivity(
            activitySearchHistory = history,
            activityListing = activityListing,
            title = "AI 공모전",
            organizer = "과기부",
            url = "https://contest.kr/1",
            matchScore = 85,
            reason = "매칭",
        )
        whenever(recommendedActivityRepository.findAllByUserId(1L, pageable))
            .thenReturn(PageImpl(listOf(recommended), pageable, 1))

        val result = service.getRecommendedActivities(1L, pageable)

        assertEquals(1, result.totalElements)
        assertEquals(85, result.content[0].matchScore)
    }

    @Test
    fun `deleteRecommendedActivity deletes own activity`() {
        val history = ActivitySearchHistory(user, "IT", null, 1)
        val recommended = RecommendedActivity(
            activitySearchHistory = history,
            activityListing = activityListing,
            title = "AI 공모전",
            organizer = "과기부",
            url = "https://contest.kr/1",
            matchScore = 85,
            reason = "매칭",
        )
        whenever(recommendedActivityRepository.findByIdAndActivitySearchHistoryUserId(1L, 1L))
            .thenReturn(recommended)

        service.deleteRecommendedActivity(1L, 1L)

        verify(recommendedActivityRepository).delete(recommended)
    }

    @Test
    fun `deleteRecommendedActivity throws for invalid id`() {
        whenever(recommendedActivityRepository.findByIdAndActivitySearchHistoryUserId(999L, 1L))
            .thenReturn(null)

        assertThrows<NoSuchElementException> {
            service.deleteRecommendedActivity(1L, 999L)
        }
    }
}
