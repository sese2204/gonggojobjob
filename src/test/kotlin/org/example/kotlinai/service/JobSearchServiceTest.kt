package org.example.kotlinai.service

import org.example.kotlinai.dto.request.JobSearchRequest
import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.repository.JobListingRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobSearchServiceTest {

    private lateinit var jobListingRepository: JobListingRepository
    private lateinit var geminiService: GeminiService
    private lateinit var jobSearchService: JobSearchService

    private val sampleListings = listOf(
        JobListing("React 개발자", "회사A", "https://a.com", "React 기반", LocalDateTime.now()).apply { },
        JobListing("Java 개발자", "회사B", "https://b.com", "Spring Boot 기반", LocalDateTime.now().minusHours(1)).apply { },
        JobListing("iOS 개발자", "회사C", "https://c.com", "Swift 기반", LocalDateTime.now().minusHours(2)).apply { },
    )

    @BeforeEach
    fun setUp() {
        jobListingRepository = mock()
        geminiService = mock()
        jobSearchService = JobSearchService(jobListingRepository, geminiService)
    }

    @Test
    fun `search throws IllegalArgumentException when both tags and query are empty`() {
        assertThrows<IllegalArgumentException> {
            jobSearchService.search(JobSearchRequest(tags = emptyList(), query = ""))
        }
    }

    @Test
    fun `search returns empty response when no listings exist`() {
        whenever(jobListingRepository.findTop10ByOrderByCollectedAtDesc()).thenReturn(emptyList())
        whenever(jobListingRepository.count()).thenReturn(0L)

        val result = jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        assertTrue(result.jobs.isEmpty())
        assertEquals(0, result.totalCount)
    }

    @Test
    fun `search sorts jobs by match descending`() {
        whenever(jobListingRepository.findTop10ByOrderByCollectedAtDesc()).thenReturn(sampleListings)
        whenever(jobListingRepository.count()).thenReturn(3L)
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(
            listOf(
                AiMatchResult("0", 50, "부분 일치"),
                AiMatchResult("0", 90, "높은 일치"),
                AiMatchResult("0", 30, "낮은 일치"),
            )
        )

        val result = jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        assertTrue(result.jobs[0].match >= result.jobs[1].match)
        assertTrue(result.jobs[1].match >= result.jobs[2].match)
    }

    @Test
    fun `search clamps match scores to 0-100`() {
        whenever(jobListingRepository.findTop10ByOrderByCollectedAtDesc()).thenReturn(
            listOf(sampleListings[0])
        )
        whenever(jobListingRepository.count()).thenReturn(1L)
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(
            listOf(AiMatchResult("0", 150, "점수 초과"))
        )

        val result = jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        assertEquals(100, result.jobs[0].match)
    }

    @Test
    fun `search applies fallback reason when AI returns blank reason`() {
        whenever(jobListingRepository.findTop10ByOrderByCollectedAtDesc()).thenReturn(
            listOf(sampleListings[0])
        )
        whenever(jobListingRepository.count()).thenReturn(1L)
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(
            listOf(AiMatchResult("0", 75, ""))
        )

        val result = jobSearchService.search(JobSearchRequest(tags = listOf("React")))

        assertEquals("AI 분석 결과 관련 공고입니다.", result.jobs[0].reason)
    }
}
