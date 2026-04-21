package org.example.kotlinai.service

import org.example.kotlinai.config.RagProperties
import org.example.kotlinai.dto.request.JobSearchRequest
import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.service.AiMatchResponse
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.repository.JobListingRepository
import org.example.kotlinai.repository.SearchHistoryRepository
import org.example.kotlinai.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.example.kotlinai.entity.SearchHistory
import org.example.kotlinai.entity.User
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobSearchServiceTest {

    private lateinit var jobListingRepository: JobListingRepository
    private lateinit var geminiService: GeminiService
    private lateinit var hybridSearchService: HybridSearchService
    private lateinit var userRepository: UserRepository
    private lateinit var searchHistoryRepository: SearchHistoryRepository
    private lateinit var searchCacheService: SearchCacheService
    private lateinit var jobSearchService: JobSearchService
    private val ragProperties = RagProperties()

    private val sampleListings = listOf(
        JobListing("React 개발자", "회사A", "https://a.com", "React 기반", LocalDateTime.now()).apply { },
        JobListing("Java 개발자", "회사B", "https://b.com", "Spring Boot 기반", LocalDateTime.now().minusHours(1)).apply { },
        JobListing("iOS 개발자", "회사C", "https://c.com", "Swift 기반", LocalDateTime.now().minusHours(2)).apply { },
    )

    @BeforeEach
    fun setUp() {
        jobListingRepository = mock()
        geminiService = mock()
        hybridSearchService = mock()
        userRepository = mock()
        searchHistoryRepository = mock()
        searchCacheService = SearchCacheService()
        jobSearchService = JobSearchService(jobListingRepository, geminiService, hybridSearchService, ragProperties, userRepository, searchHistoryRepository, searchCacheService)
        SecurityContextHolder.clearContext()
    }

    private fun setAuthenticatedUser(userId: Long) {
        val auth = UsernamePasswordAuthenticationToken(
            userId, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `search throws IllegalArgumentException when both tags and query are empty`() {
        assertThrows<IllegalArgumentException> {
            jobSearchService.search(JobSearchRequest(tags = emptyList(), query = ""))
        }
    }

    @Test
    fun `search returns empty response when no listings exist`() {
        whenever(hybridSearchService.search(any(), any(), any())).thenReturn(emptyList())
        whenever(jobListingRepository.count()).thenReturn(0L)

        val result = jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        assertTrue(result.jobs.isEmpty())
        assertEquals(0, result.totalCount)
    }

    @Test
    fun `search sorts jobs by match descending`() {
        whenever(hybridSearchService.search(any(), any(), any())).thenReturn(sampleListings)
        whenever(jobListingRepository.count()).thenReturn(3L)
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(
            AiMatchResponse(listOf(
                AiMatchResult("0", 50, "부분 일치"),
                AiMatchResult("0", 90, "높은 일치"),
                AiMatchResult("0", 30, "낮은 일치"),
            ), 1000)
        )

        val result = jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        assertTrue(result.jobs[0].match >= result.jobs[1].match)
        assertTrue(result.jobs[1].match >= result.jobs[2].match)
    }

    @Test
    fun `search clamps match scores to 0-100`() {
        whenever(hybridSearchService.search(any(), any(), any())).thenReturn(
            listOf(sampleListings[0])
        )
        whenever(jobListingRepository.count()).thenReturn(1L)
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(
            AiMatchResponse(listOf(AiMatchResult("0", 150, "점수 초과")), 500)
        )

        val result = jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        assertEquals(100, result.jobs[0].match)
    }

    @Test
    fun `search applies fallback reason when AI returns blank reason`() {
        whenever(hybridSearchService.search(any(), any(), any())).thenReturn(
            listOf(sampleListings[0])
        )
        whenever(jobListingRepository.count()).thenReturn(1L)
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(
            AiMatchResponse(listOf(AiMatchResult("0", 75, "")), 500)
        )

        val result = jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        assertEquals("AI 분석 결과 관련 공고입니다.", result.jobs[0].reason)
    }

    @Test
    fun `search saves history when user is authenticated`() {
        setAuthenticatedUser(1L)
        val user = User("test@test.com", "테스트")
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(hybridSearchService.search(any(), any(), any())).thenReturn(sampleListings)
        whenever(jobListingRepository.count()).thenReturn(3L)
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(
            AiMatchResponse(listOf(AiMatchResult("0", 80, "일치")), 500)
        )
        whenever(searchHistoryRepository.save(any<SearchHistory>())).thenAnswer { it.arguments[0] }

        jobSearchService.search(JobSearchRequest(tags = listOf("React"), query = "프론트"))

        val captor = argumentCaptor<SearchHistory>()
        verify(searchHistoryRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("React", saved.tagsString)
        assertEquals("프론트", saved.query)
        assertEquals(3, saved.resultCount)
        assertEquals(3, saved.recommendedJobs.size)
    }

    @Test
    fun `search does not save history when user is not authenticated`() {
        whenever(hybridSearchService.search(any(), any(), any())).thenReturn(sampleListings)
        whenever(jobListingRepository.count()).thenReturn(3L)
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(AiMatchResponse(emptyList(), 0))

        jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        verify(searchHistoryRepository, never()).save(any<SearchHistory>())
    }

    @Test
    fun `search skips save when userId does not exist`() {
        setAuthenticatedUser(999L)
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())
        whenever(hybridSearchService.search(any(), any(), any())).thenReturn(sampleListings)
        whenever(jobListingRepository.count()).thenReturn(3L)
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(AiMatchResponse(emptyList(), 0))

        jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        verify(searchHistoryRepository, never()).save(any<SearchHistory>())
    }

    @Test
    fun `search saves history with zero results when authenticated`() {
        setAuthenticatedUser(1L)
        val user = User("test@test.com", "테스트")
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(hybridSearchService.search(any(), any(), any())).thenReturn(emptyList())
        whenever(jobListingRepository.count()).thenReturn(0L)
        whenever(searchHistoryRepository.save(any<SearchHistory>())).thenAnswer { it.arguments[0] }

        jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        val captor = argumentCaptor<SearchHistory>()
        verify(searchHistoryRepository).save(captor.capture())
        assertEquals(0, captor.firstValue.resultCount)
        assertTrue(captor.firstValue.recommendedJobs.isEmpty())
    }
}
