package org.example.kotlinai.service

import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.entity.RecommendedJob
import org.example.kotlinai.entity.SearchHistory
import org.example.kotlinai.entity.User
import org.example.kotlinai.repository.RecommendedJobRepository
import org.example.kotlinai.repository.SearchHistoryRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchHistoryServiceTest {

    private lateinit var searchHistoryRepository: SearchHistoryRepository
    private lateinit var recommendedJobRepository: RecommendedJobRepository
    private lateinit var searchHistoryService: SearchHistoryService

    private val user = User("test@test.com", "테스트")
    private val pageable = PageRequest.of(0, 20)

    @BeforeEach
    fun setUp() {
        searchHistoryRepository = mock()
        recommendedJobRepository = mock()
        searchHistoryService = SearchHistoryService(searchHistoryRepository, recommendedJobRepository)
    }

    @Test
    fun `getSearchHistories returns paginated history`() {
        val history1 = SearchHistory(user, "Java,Spring", "백엔드", 5)
        val history2 = SearchHistory(user, null, "프론트엔드", 3)
        whenever(searchHistoryRepository.findAllByUserIdOrderBySearchedAtDesc(any(), any()))
            .thenReturn(PageImpl(listOf(history1, history2), pageable, 2))

        val result = searchHistoryService.getSearchHistories(1L, pageable)

        assertEquals(2, result.totalElements)
        assertEquals(listOf("Java", "Spring"), result.content[0].tags)
        assertEquals("백엔드", result.content[0].query)
        assertTrue(result.content[1].tags.isEmpty())
    }

    @Test
    fun `getSearchDetail returns detail with recommended jobs`() {
        val history = SearchHistory(user, "React", "프론트", 1)
        val listing = JobListing("React 개발자", "회사A", "https://a.com", "설명", LocalDateTime.now())
        val recommendedJob = RecommendedJob(history, listing, "React 개발자", "회사A", "https://a.com", 85, "기술 일치")
        history.recommendedJobs.add(recommendedJob)

        whenever(searchHistoryRepository.findById(1L)).thenReturn(Optional.of(history))

        val result = searchHistoryService.getSearchDetail(1L)

        assertEquals(listOf("React"), result.tags)
        assertEquals("프론트", result.query)
        assertEquals(1, result.recommendedJobs.size)
        assertEquals(85, result.recommendedJobs[0].matchScore)
        assertEquals("React 개발자", result.recommendedJobs[0].title)
    }

    @Test
    fun `getSearchDetail throws NoSuchElementException for invalid id`() {
        whenever(searchHistoryRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> {
            searchHistoryService.getSearchDetail(999L)
        }
    }

    @Test
    fun `getRecommendedJobs returns paginated jobs across searches`() {
        val history = SearchHistory(user, "Java", "백엔드", 1)
        val listing = JobListing("Java 개발자", "회사B", "https://b.com", "설명", LocalDateTime.now())
        val rec = RecommendedJob(history, listing, "Java 개발자", "회사B", "https://b.com", 90, "높은 일치")
        whenever(recommendedJobRepository.findAllByUserId(any(), any()))
            .thenReturn(PageImpl(listOf(rec), pageable, 1))

        val result = searchHistoryService.getRecommendedJobs(1L, pageable)

        assertEquals(1, result.totalElements)
        assertEquals(90, result.content[0].matchScore)
        assertEquals("Java 개발자", result.content[0].title)
    }
}
