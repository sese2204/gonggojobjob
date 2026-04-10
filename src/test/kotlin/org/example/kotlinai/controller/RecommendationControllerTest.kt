package org.example.kotlinai.controller

import org.example.kotlinai.dto.response.*
import org.example.kotlinai.service.RecommendationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
class RecommendationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var recommendationService: RecommendationService

    private val sampleResponse = CategoryRecommendationResponse(
        jobCategories = listOf(
            JobCategoryGroup(
                category = "IT_DEV",
                displayName = "IT/개발",
                jobs = listOf(
                    JobRecommendationItem(
                        jobListingId = 1L,
                        title = "Spring 백엔드 개발자",
                        company = "회사A",
                        url = "https://example.com/1",
                        matchScore = 92,
                        reason = "Spring Boot 기반 백엔드 공고",
                    ),
                ),
            ),
        ),
        activityCategories = listOf(
            ActivityCategoryGroup(
                category = "IT_CONTEST",
                displayName = "IT/SW 공모전",
                activities = listOf(
                    ActivityRecommendationItem(
                        activityListingId = 10L,
                        title = "AI 해커톤",
                        organizer = "주최사",
                        category = "IT",
                        startDate = "2026-04-01",
                        endDate = "2026-04-30",
                        url = "https://example.com/10",
                        matchScore = 88,
                        reason = "AI 관련 공모전",
                    ),
                ),
            ),
        ),
        generatedAt = LocalDate.of(2026, 4, 10),
    )

    @Test
    fun `GET recommendations returns 200 with category groups`() {
        whenever(recommendationService.getRecommendations()).thenReturn(sampleResponse)

        mockMvc.get("/api/recommendations")
            .andExpect {
                status { isOk() }
                jsonPath("$.generatedAt") { value("2026-04-10") }
                jsonPath("$.jobCategories[0].category") { value("IT_DEV") }
                jsonPath("$.jobCategories[0].displayName") { value("IT/개발") }
                jsonPath("$.jobCategories[0].jobs[0].title") { value("Spring 백엔드 개발자") }
                jsonPath("$.jobCategories[0].jobs[0].matchScore") { value(92) }
                jsonPath("$.activityCategories[0].category") { value("IT_CONTEST") }
                jsonPath("$.activityCategories[0].activities[0].title") { value("AI 해커톤") }
                jsonPath("$.activityCategories[0].activities[0].matchScore") { value(88) }
            }
    }

    @Test
    fun `GET recommendations returns empty when no data`() {
        whenever(recommendationService.getRecommendations()).thenReturn(
            CategoryRecommendationResponse(
                jobCategories = emptyList(),
                activityCategories = emptyList(),
                generatedAt = null,
            ),
        )

        mockMvc.get("/api/recommendations")
            .andExpect {
                status { isOk() }
                jsonPath("$.jobCategories") { isEmpty() }
                jsonPath("$.activityCategories") { isEmpty() }
                jsonPath("$.generatedAt") { value(null as Any?) }
            }
    }

    @Test
    fun `GET recommendations is accessible without authentication`() {
        whenever(recommendationService.getRecommendations()).thenReturn(sampleResponse)

        mockMvc.get("/api/recommendations")
            .andExpect {
                status { isOk() }
            }
    }
}
