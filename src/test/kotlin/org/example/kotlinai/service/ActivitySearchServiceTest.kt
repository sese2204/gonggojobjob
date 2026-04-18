package org.example.kotlinai.service

import org.example.kotlinai.config.RagProperties
import org.example.kotlinai.dto.request.ActivitySearchRequest
import org.example.kotlinai.dto.response.AiMatchResult
import org.example.kotlinai.entity.ActivityListing
import org.example.kotlinai.entity.ActivitySearchHistory
import org.example.kotlinai.entity.User
import org.example.kotlinai.global.exception.DailySearchLimitExceededException
import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.repository.ActivitySearchHistoryRepository
import org.example.kotlinai.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

class ActivitySearchServiceTest {

    private lateinit var activityListingRepository: ActivityListingRepository
    private lateinit var geminiService: GeminiService
    private lateinit var activityHybridSearchService: ActivityHybridSearchService
    private lateinit var userRepository: UserRepository
    private lateinit var activitySearchHistoryRepository: ActivitySearchHistoryRepository
    private lateinit var searchCacheService: SearchCacheService
    private lateinit var activitySearchService: ActivitySearchService
    private val ragProperties = RagProperties()

    private val sampleListings = listOf(
        ActivityListing(
            title = "AI 공모전", organizer = "과기부", url = "https://a.com",
            category = "IT/SW", description = "AI 관련", sourceName = "allcon", sourceId = "1",
        ),
        ActivityListing(
            title = "마케팅 대외활동", organizer = "삼성", url = "https://b.com",
            category = "마케팅", description = "마케팅 서포터즈", sourceName = "wevity", sourceId = "2",
        ),
        ActivityListing(
            title = "디자인 공모전", organizer = "카카오", url = "https://c.com",
            category = "디자인", description = "UX/UI", sourceName = "thinkcontest", sourceId = "3",
        ),
    )

    @BeforeEach
    fun setUp() {
        activityListingRepository = mock()
        geminiService = mock()
        activityHybridSearchService = mock()
        userRepository = mock()
        activitySearchHistoryRepository = mock()
        searchCacheService = SearchCacheService()
        activitySearchService = ActivitySearchService(
            activityListingRepository, geminiService, activityHybridSearchService,
            ragProperties, userRepository, activitySearchHistoryRepository, searchCacheService,
        )
        SecurityContextHolder.clearContext()
    }

    private fun setAuthenticatedUser(userId: Long) {
        val auth = UsernamePasswordAuthenticationToken(
            userId, null, listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `search throws when both tags and query are empty`() {
        assertThrows<IllegalArgumentException> {
            activitySearchService.search(ActivitySearchRequest(tags = emptyList(), query = ""))
        }
    }

    @Test
    fun `search throws when query exceeds 500 chars`() {
        assertThrows<IllegalArgumentException> {
            activitySearchService.search(ActivitySearchRequest(query = "a".repeat(501)))
        }
    }

    @Test
    fun `search returns empty response when no listings match`() {
        whenever(activityHybridSearchService.search(any(), any(), any())).thenReturn(emptyList())
        whenever(activityListingRepository.count()).thenReturn(0L)

        val result = activitySearchService.search(ActivitySearchRequest(tags = listOf("IT")))

        assertTrue(result.activities.isEmpty())
        assertEquals(0, result.totalCount)
    }

    @Test
    fun `search sorts activities by match descending`() {
        whenever(activityHybridSearchService.search(any(), any(), any())).thenReturn(sampleListings)
        whenever(activityListingRepository.count()).thenReturn(3L)
        whenever(geminiService.matchActivities(any(), any(), any())).thenReturn(
            listOf(
                AiMatchResult("0", 50, "부분 일치"),
                AiMatchResult("0", 90, "높은 일치"),
                AiMatchResult("0", 30, "낮은 일치"),
            ),
        )

        val result = activitySearchService.search(ActivitySearchRequest(tags = listOf("IT")))

        assertTrue(result.activities[0].match >= result.activities[1].match)
        assertTrue(result.activities[1].match >= result.activities[2].match)
    }

    @Test
    fun `search clamps match scores to 0-100`() {
        whenever(activityHybridSearchService.search(any(), any(), any())).thenReturn(listOf(sampleListings[0]))
        whenever(activityListingRepository.count()).thenReturn(1L)
        whenever(geminiService.matchActivities(any(), any(), any())).thenReturn(
            listOf(AiMatchResult("0", 150, "점수 초과")),
        )

        val result = activitySearchService.search(ActivitySearchRequest(tags = listOf("IT")))

        assertEquals(100, result.activities[0].match)
    }

    @Test
    fun `search applies fallback reason when AI returns blank reason`() {
        whenever(activityHybridSearchService.search(any(), any(), any())).thenReturn(listOf(sampleListings[0]))
        whenever(activityListingRepository.count()).thenReturn(1L)
        whenever(geminiService.matchActivities(any(), any(), any())).thenReturn(
            listOf(AiMatchResult("0", 75, "")),
        )

        val result = activitySearchService.search(ActivitySearchRequest(tags = listOf("IT")))

        assertEquals("AI 분석 결과 관련 활동입니다.", result.activities[0].reason)
    }

    @Test
    fun `search includes activity-specific fields in results`() {
        whenever(activityHybridSearchService.search(any(), any(), any())).thenReturn(listOf(sampleListings[0]))
        whenever(activityListingRepository.count()).thenReturn(1L)
        whenever(geminiService.matchActivities(any(), any(), any())).thenReturn(
            listOf(AiMatchResult("0", 80, "매칭")),
        )

        val result = activitySearchService.search(ActivitySearchRequest(tags = listOf("IT")))

        val activity = result.activities[0]
        assertEquals("AI 공모전", activity.title)
        assertEquals("과기부", activity.organizer)
        assertEquals("IT/SW", activity.category)
    }

    @Test
    fun `search saves history when user is authenticated`() {
        setAuthenticatedUser(1L)
        val user = User("test@test.com", "테스트")
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(activityHybridSearchService.search(any(), any(), any())).thenReturn(sampleListings)
        whenever(activityListingRepository.count()).thenReturn(3L)
        whenever(geminiService.matchActivities(any(), any(), any())).thenReturn(
            listOf(AiMatchResult("0", 80, "일치")),
        )
        whenever(activitySearchHistoryRepository.save(any<ActivitySearchHistory>())).thenAnswer { it.arguments[0] }

        activitySearchService.search(ActivitySearchRequest(tags = listOf("IT"), query = "공모전"))

        val captor = argumentCaptor<ActivitySearchHistory>()
        verify(activitySearchHistoryRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("IT", saved.tagsString)
        assertEquals("공모전", saved.query)
        assertEquals(3, saved.resultCount)
        assertEquals(3, saved.recommendedActivities.size)
    }

    @Test
    fun `search does not save history when user is not authenticated`() {
        whenever(activityHybridSearchService.search(any(), any(), any())).thenReturn(sampleListings)
        whenever(activityListingRepository.count()).thenReturn(3L)
        whenever(geminiService.matchActivities(any(), any(), any())).thenReturn(emptyList())

        activitySearchService.search(ActivitySearchRequest(tags = listOf("IT")))

        verify(activitySearchHistoryRepository, never()).save(any<ActivitySearchHistory>())
    }

    @Test
    fun `search throws DailySearchLimitExceededException when limit reached`() {
        setAuthenticatedUser(1L)
        whenever(activitySearchHistoryRepository.countByUserIdAndSearchedAtAfter(any(), any())).thenReturn(15L)

        assertThrows<DailySearchLimitExceededException> {
            activitySearchService.search(ActivitySearchRequest(tags = listOf("IT")))
        }
    }
}
