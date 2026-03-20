package org.example.kotlinai.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.global.exception.AiServiceException
import org.example.kotlinai.repository.JobListingRepository
import org.example.kotlinai.service.GeminiService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class JobSearchControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var geminiService: GeminiService

    @MockBean
    private lateinit var jobListingRepository: JobListingRepository

    private val objectMapper = jacksonObjectMapper()

    private val fakeListings = listOf(
        JobListing(title = "백엔드 개발자", company = "테스트 A", url = "https://example.com/1", sourceName = "mock", sourceId = "t1"),
        JobListing(title = "프론트엔드 개발자", company = "테스트 B", url = "https://example.com/2", sourceName = "mock", sourceId = "t2"),
    )

    @BeforeEach
    fun setUp() {
        whenever(jobListingRepository.findTop10ByOrderByCollectedAtDesc()).thenReturn(fakeListings)
        whenever(jobListingRepository.count()).thenReturn(2L)
    }

    @Test
    fun `POST search returns 200 with AI-scored jobs`() {
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(
            listOf(
                AiMatchResult("0", 90, "React 기술이 일치합니다."),
                AiMatchResult("1", 40, "기술 스택이 다소 다릅니다."),
            )
        )

        mockMvc.post("/api/jobs/search") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("tags" to listOf("React", "Node.js"), "query" to "프론트엔드 개발자")
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.jobs") { isArray() }
            jsonPath("$.totalCount") { isNumber() }
            jsonPath("$.newTodayCount") { isNumber() }
        }
    }

    @Test
    fun `POST search returns 400 when both tags and query are empty`() {
        mockMvc.post("/api/jobs/search") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("tags" to emptyList<String>(), "query" to "")
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
        }
    }

    @Test
    fun `POST search returns 503 when AI service fails`() {
        whenever(geminiService.matchJobs(any(), any(), any()))
            .thenThrow(AiServiceException("Gemini API 호출 실패"))

        mockMvc.post("/api/jobs/search") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("tags" to listOf("React"), "query" to "")
            )
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.status") { value(503) }
        }
    }
}
