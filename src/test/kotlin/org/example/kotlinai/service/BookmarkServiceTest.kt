package org.example.kotlinai.service

import org.example.kotlinai.dto.request.CreateBookmarkRequest
import org.example.kotlinai.dto.request.CreateCustomBookmarkRequest
import org.example.kotlinai.dto.request.UpdateBookmarkRequest
import org.example.kotlinai.entity.*
import org.example.kotlinai.global.exception.DuplicateBookmarkException
import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.repository.BookmarkedJobRepository
import org.example.kotlinai.repository.JobListingRepository
import org.example.kotlinai.repository.RecommendedActivityRepository
import org.example.kotlinai.repository.RecommendedJobRepository
import org.example.kotlinai.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookmarkServiceTest {

    private lateinit var bookmarkedJobRepository: BookmarkedJobRepository
    private lateinit var jobListingRepository: JobListingRepository
    private lateinit var activityListingRepository: ActivityListingRepository
    private lateinit var recommendedJobRepository: RecommendedJobRepository
    private lateinit var recommendedActivityRepository: RecommendedActivityRepository
    private lateinit var userRepository: UserRepository
    private lateinit var bookmarkService: BookmarkService

    private val user = User("test@test.com", "테스트")
    private val jobListing = JobListing("백엔드 개발자", "회사A", "https://a.com", "Spring Boot 경험자 우대")
    private val activityListing = ActivityListing(
        title = "AI 공모전",
        organizer = "과기부",
        url = "https://contest.kr/1",
        category = "IT/SW",
        startDate = "2026-04-01",
        endDate = "2026-05-30",
        description = "인공지능 관련 공모전",
        sourceName = "allcon",
        sourceId = "100",
    )
    private val pageable = PageRequest.of(0, 20)

    @BeforeEach
    fun setUp() {
        bookmarkedJobRepository = mock()
        jobListingRepository = mock()
        activityListingRepository = mock()
        recommendedJobRepository = mock()
        recommendedActivityRepository = mock()
        userRepository = mock()
        bookmarkService = BookmarkService(
            bookmarkedJobRepository,
            jobListingRepository,
            activityListingRepository,
            recommendedJobRepository,
            recommendedActivityRepository,
            userRepository,
        )

        whenever(userRepository.getReferenceById(1L)).thenReturn(user)
    }

    // ===== Job Bookmark Tests (existing) =====

    @Test
    fun `createBookmark from jobListingId saves snapshot`() {
        whenever(bookmarkedJobRepository.existsByUserIdAndJobListingId(1L, 10L)).thenReturn(false)
        whenever(jobListingRepository.findById(10L)).thenReturn(Optional.of(jobListing))
        whenever(bookmarkedJobRepository.save(any<BookmarkedJob>())).thenAnswer { it.arguments[0] }

        val result = bookmarkService.createBookmark(1L, CreateBookmarkRequest(jobListingId = 10L))

        assertEquals("백엔드 개발자", result.title)
        assertEquals("회사A", result.company)
        assertEquals(BookmarkType.JOB, result.type)
        assertEquals(ApplicationStatus.NOT_APPLIED, result.status)
    }

    @Test
    fun `createBookmark from recommendedJobId copies snapshot`() {
        val searchHistory = SearchHistory(user, "Java", "백엔드", 1)
        val recommendedJob = RecommendedJob(searchHistory, jobListing, "백엔드 개발자", "회사A", "https://a.com", 85, "좋은 매칭")

        whenever(recommendedJobRepository.findById(20L)).thenReturn(Optional.of(recommendedJob))
        whenever(bookmarkedJobRepository.existsByUserIdAndJobListingId(any(), any())).thenReturn(false)
        whenever(bookmarkedJobRepository.existsByUserIdAndUrl(any(), any())).thenReturn(false)
        whenever(jobListingRepository.findById(any())).thenReturn(Optional.of(jobListing))
        whenever(bookmarkedJobRepository.save(any<BookmarkedJob>())).thenAnswer { it.arguments[0] }

        val result = bookmarkService.createBookmark(1L, CreateBookmarkRequest(recommendedJobId = 20L))

        assertEquals("백엔드 개발자", result.title)
        assertEquals(BookmarkType.JOB, result.type)
    }

    @Test
    fun `createBookmark throws when no id provided`() {
        assertThrows<IllegalArgumentException> {
            bookmarkService.createBookmark(1L, CreateBookmarkRequest())
        }
    }

    @Test
    fun `createBookmark throws when multiple ids provided`() {
        assertThrows<IllegalArgumentException> {
            bookmarkService.createBookmark(1L, CreateBookmarkRequest(jobListingId = 1L, recommendedJobId = 2L))
        }
    }

    @Test
    fun `createBookmark throws DuplicateBookmarkException for same jobListingId`() {
        whenever(bookmarkedJobRepository.existsByUserIdAndJobListingId(1L, 10L)).thenReturn(true)

        assertThrows<DuplicateBookmarkException> {
            bookmarkService.createBookmark(1L, CreateBookmarkRequest(jobListingId = 10L))
        }
    }

    @Test
    fun `createBookmark throws NoSuchElementException for invalid jobListingId`() {
        whenever(bookmarkedJobRepository.existsByUserIdAndJobListingId(1L, 999L)).thenReturn(false)
        whenever(jobListingRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> {
            bookmarkService.createBookmark(1L, CreateBookmarkRequest(jobListingId = 999L))
        }
    }

    // ===== Activity Bookmark Tests (new) =====

    @Test
    fun `createBookmark from activityListingId saves activity snapshot`() {
        whenever(bookmarkedJobRepository.existsByUserIdAndActivityListingId(1L, 10L)).thenReturn(false)
        whenever(activityListingRepository.findById(10L)).thenReturn(Optional.of(activityListing))
        whenever(bookmarkedJobRepository.save(any<BookmarkedJob>())).thenAnswer { it.arguments[0] }

        val result = bookmarkService.createBookmark(1L, CreateBookmarkRequest(activityListingId = 10L))

        assertEquals("AI 공모전", result.title)
        assertEquals("과기부", result.company)
        assertEquals(BookmarkType.ACTIVITY, result.type)
        assertEquals("IT/SW", result.category)
        assertEquals("2026-04-01", result.startDate)
        assertEquals("2026-05-30", result.endDate)
    }

    @Test
    fun `createBookmark from recommendedActivityId copies activity snapshot`() {
        val searchHistory = ActivitySearchHistory(user, "IT", "공모전", 1)
        val recommendedActivity = RecommendedActivity(
            activitySearchHistory = searchHistory,
            activityListing = activityListing,
            title = "AI 공모전",
            organizer = "과기부",
            url = "https://contest.kr/1",
            category = "IT/SW",
            startDate = "2026-04-01",
            endDate = "2026-05-30",
            matchScore = 90,
            reason = "IT 분야 공모전",
        )

        whenever(recommendedActivityRepository.findById(30L)).thenReturn(Optional.of(recommendedActivity))
        whenever(bookmarkedJobRepository.existsByUserIdAndActivityListingId(any(), any())).thenReturn(false)
        whenever(bookmarkedJobRepository.existsByUserIdAndUrl(any(), any())).thenReturn(false)
        whenever(activityListingRepository.findById(any())).thenReturn(Optional.of(activityListing))
        whenever(bookmarkedJobRepository.save(any<BookmarkedJob>())).thenAnswer { it.arguments[0] }

        val result = bookmarkService.createBookmark(1L, CreateBookmarkRequest(recommendedActivityId = 30L))

        assertEquals("AI 공모전", result.title)
        assertEquals(BookmarkType.ACTIVITY, result.type)
        assertEquals("IT/SW", result.category)
    }

    @Test
    fun `createBookmark throws DuplicateBookmarkException for same activityListingId`() {
        whenever(bookmarkedJobRepository.existsByUserIdAndActivityListingId(1L, 10L)).thenReturn(true)

        assertThrows<DuplicateBookmarkException> {
            bookmarkService.createBookmark(1L, CreateBookmarkRequest(activityListingId = 10L))
        }
    }

    @Test
    fun `createCustomBookmark with ACTIVITY type saves activity fields`() {
        whenever(bookmarkedJobRepository.save(any<BookmarkedJob>())).thenAnswer { it.arguments[0] }

        val result = bookmarkService.createCustomBookmark(
            1L,
            CreateCustomBookmarkRequest(
                title = "마케팅 공모전",
                company = "삼성",
                type = BookmarkType.ACTIVITY,
                category = "마케팅",
                startDate = "2026-05-01",
                endDate = "2026-06-30",
            ),
        )

        assertEquals("마케팅 공모전", result.title)
        assertEquals(BookmarkType.ACTIVITY, result.type)
        assertEquals("마케팅", result.category)
        assertEquals("2026-05-01", result.startDate)
    }

    // ===== Unified Bookmark View Tests (new) =====

    @Test
    fun `getBookmarks without type returns all bookmarks`() {
        val jobBookmark = BookmarkedJob(
            user = user, type = BookmarkType.JOB, jobListing = jobListing,
            title = "백엔드 개발자", company = "회사A", url = "https://a.com", description = "설명",
        )
        whenever(bookmarkedJobRepository.findAllByUserIdOrderByBookmarkedAtDesc(1L, pageable))
            .thenReturn(PageImpl(listOf(jobBookmark), pageable, 1))

        val result = bookmarkService.getBookmarks(1L, null, null, pageable)

        assertEquals(1, result.totalElements)
    }

    @Test
    fun `getBookmarks filters by type ACTIVITY`() {
        val activityBookmark = BookmarkedJob(
            user = user, type = BookmarkType.ACTIVITY, activityListing = activityListing,
            title = "AI 공모전", company = "과기부", url = "https://contest.kr/1",
            category = "IT/SW", startDate = "2026-04-01", endDate = "2026-05-30",
        )
        whenever(
            bookmarkedJobRepository.findAllByUserIdAndTypeOrderByBookmarkedAtDesc(1L, BookmarkType.ACTIVITY, pageable),
        ).thenReturn(PageImpl(listOf(activityBookmark), pageable, 1))

        val result = bookmarkService.getBookmarks(1L, BookmarkType.ACTIVITY, null, pageable)

        assertEquals(1, result.totalElements)
        assertEquals(BookmarkType.ACTIVITY, result.content[0].type)
        assertEquals("IT/SW", result.content[0].category)
    }

    @Test
    fun `getBookmarks filters by type and status`() {
        val activityBookmark = BookmarkedJob(
            user = user, type = BookmarkType.ACTIVITY, activityListing = activityListing,
            title = "AI 공모전", company = "과기부", url = "https://contest.kr/1",
        ).apply { status = ApplicationStatus.APPLIED }
        whenever(
            bookmarkedJobRepository.findAllByUserIdAndTypeAndStatusOrderByBookmarkedAtDesc(
                1L, BookmarkType.ACTIVITY, ApplicationStatus.APPLIED, pageable,
            ),
        ).thenReturn(PageImpl(listOf(activityBookmark), pageable, 1))

        val result = bookmarkService.getBookmarks(1L, BookmarkType.ACTIVITY, ApplicationStatus.APPLIED, pageable)

        assertEquals(1, result.totalElements)
        assertEquals(ApplicationStatus.APPLIED, result.content[0].status)
    }

    @Test
    fun `getBookmarks filters by status only`() {
        val bookmark = BookmarkedJob(
            user = user, type = BookmarkType.JOB, jobListing = jobListing,
            title = "백엔드 개발자", company = "회사A",
        ).apply { status = ApplicationStatus.APPLIED }
        whenever(
            bookmarkedJobRepository.findAllByUserIdAndStatusOrderByBookmarkedAtDesc(1L, ApplicationStatus.APPLIED, pageable),
        ).thenReturn(PageImpl(listOf(bookmark), pageable, 1))

        val result = bookmarkService.getBookmarks(1L, null, ApplicationStatus.APPLIED, pageable)

        assertEquals(1, result.totalElements)
        assertEquals(ApplicationStatus.APPLIED, result.content[0].status)
    }

    // ===== Custom Bookmark Validation (existing) =====

    @Test
    fun `createCustomBookmark saves with direct input`() {
        whenever(bookmarkedJobRepository.existsByUserIdAndUrl(1L, "https://custom.com")).thenReturn(false)
        whenever(bookmarkedJobRepository.save(any<BookmarkedJob>())).thenAnswer { it.arguments[0] }

        val result = bookmarkService.createCustomBookmark(
            1L,
            CreateCustomBookmarkRequest("프론트 개발자", "회사B", "https://custom.com", "React 필수"),
        )

        assertEquals("프론트 개발자", result.title)
        assertEquals(BookmarkType.JOB, result.type)
    }

    @Test
    fun `createCustomBookmark without url skips dedup check`() {
        whenever(bookmarkedJobRepository.save(any<BookmarkedJob>())).thenAnswer { it.arguments[0] }

        bookmarkService.createCustomBookmark(1L, CreateCustomBookmarkRequest("디자이너", "회사C"))

        verify(bookmarkedJobRepository, never()).existsByUserIdAndUrl(any(), any())
    }

    @Test
    fun `createCustomBookmark throws when title is blank`() {
        assertThrows<IllegalArgumentException> {
            bookmarkService.createCustomBookmark(1L, CreateCustomBookmarkRequest("", "회사"))
        }
    }

    @Test
    fun `createCustomBookmark throws when company is blank`() {
        assertThrows<IllegalArgumentException> {
            bookmarkService.createCustomBookmark(1L, CreateCustomBookmarkRequest("개발자", ""))
        }
    }

    // ===== Update/Delete (existing) =====

    @Test
    fun `updateBookmark changes status and memo`() {
        val bookmark = BookmarkedJob(
            user = user, type = BookmarkType.JOB, jobListing = jobListing,
            title = "백엔드 개발자", company = "회사A", url = "https://a.com",
        )
        whenever(bookmarkedJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(bookmark)

        val result = bookmarkService.updateBookmark(
            1L, 1L,
            UpdateBookmarkRequest(status = ApplicationStatus.INTERVIEWING, memo = "4/20 면접"),
        )

        assertEquals(ApplicationStatus.INTERVIEWING, result.status)
        assertEquals("4/20 면접", result.memo)
    }

    @Test
    fun `updateBookmark throws when memo exceeds 500 chars`() {
        val bookmark = BookmarkedJob(
            user = user, type = BookmarkType.JOB, title = "개발자", company = "회사",
        )
        whenever(bookmarkedJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(bookmark)

        assertThrows<IllegalArgumentException> {
            bookmarkService.updateBookmark(1L, 1L, UpdateBookmarkRequest(memo = "a".repeat(501)))
        }
    }

    @Test
    fun `deleteBookmark deletes own bookmark`() {
        val bookmark = BookmarkedJob(
            user = user, type = BookmarkType.JOB, title = "개발자", company = "회사",
        )
        whenever(bookmarkedJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(bookmark)

        bookmarkService.deleteBookmark(1L, 1L)

        verify(bookmarkedJobRepository).delete(bookmark)
    }

    @Test
    fun `deleteBookmark throws NoSuchElementException for invalid id`() {
        whenever(bookmarkedJobRepository.findByIdAndUserId(999L, 1L)).thenReturn(null)

        assertThrows<NoSuchElementException> {
            bookmarkService.deleteBookmark(1L, 999L)
        }
    }
}
