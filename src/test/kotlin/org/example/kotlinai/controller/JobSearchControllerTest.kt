package org.example.kotlinai.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.global.exception.AiServiceException
import org.example.kotlinai.service.AiJobSummary
import org.example.kotlinai.service.GeminiService
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

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `POST search returns 200 with AI-scored jobs`() {
        whenever(geminiService.matchJobs(any(), any(), any())).thenReturn(
            listOf(
                AiMatchResult("1", 90, "React 기술이 일치합니다."),
                AiMatchResult("2", 40, "기술 스택이 다소 다릅니다."),
                AiMatchResult("3", 60, "풀스택 경험이 부분적으로 일치합니다."),
                AiMatchResult("4", 10, "데이터 엔지니어링은 관련성이 낮습니다."),
                AiMatchResult("5", 5, "iOS 개발은 관련성이 없습니다."),
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
