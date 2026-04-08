package org.example.kotlinai.service

import org.example.kotlinai.dto.request.CreateBookmarkRequest
import org.example.kotlinai.dto.request.CreateCustomBookmarkRequest
import org.example.kotlinai.dto.request.UpdateBookmarkRequest
import org.example.kotlinai.entity.*
import org.example.kotlinai.global.exception.DuplicateBookmarkException
import org.example.kotlinai.repository.BookmarkedJobRepository
import org.example.kotlinai.repository.JobListingRepository
import org.example.kotlinai.repository.RecommendedJobRepository
import org.example.kotlinai.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookmarkServiceTest {

    private lateinit var bookmarkedJobRepository: BookmarkedJobRepository
    private lateinit var jobListingRepository: JobListingRepository
    private lateinit var recommendedJobRepository: RecommendedJobRepository
    private lateinit var userRepository: UserRepository
    private lateinit var bookmarkService: BookmarkService

    private val user = User("test@test.com", "테스트")
    private val jobListing = JobListing("백엔드 개발자", "회사A", "https://a.com", "Spring Boot 경험자 우대")
    private val pageable = PageRequest.of(0, 20)

    @BeforeEach
    fun setUp() {
        bookmarkedJobRepository = mock()
        jobListingRepository = mock()
        recommendedJobRepository = mock()
        userRepository = mock()
        bookmarkService = BookmarkService(
            bookmarkedJobRepository,
            jobListingRepository,
            recommendedJobRepository,
            userRepository,
        )

        whenever(userRepository.getReferenceById(1L)).thenReturn(user)
    }

    @Test
    fun `createBookmark from jobListingId saves snapshot`() {
        whenever(bookmarkedJobRepository.existsByUserIdAndJobListingId(1L, 10L)).thenReturn(false)
        whenever(jobListingRepository.findById(10L)).thenReturn(Optional.of(jobListing))
        whenever(bookmarkedJobRepository.save(any<BookmarkedJob>())).thenAnswer { it.arguments[0] }

        val result = bookmarkService.createBookmark(1L, CreateBookmarkRequest(jobListingId = 10L))

        assertEquals("백엔드 개발자", result.title)
        assertEquals("회사A", result.company)
        assertEquals("https://a.com", result.url)
        assertEquals("Spring Boot 경험자 우대", result.description)
        assertEquals(ApplicationStatus.NOT_APPLIED, result.status)
        assertNull(result.memo)
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
        assertEquals("회사A", result.company)
        assertEquals(ApplicationStatus.NOT_APPLIED, result.status)
    }

    @Test
    fun `createBookmark throws when neither id provided`() {
        assertThrows<IllegalArgumentException> {
            bookmarkService.createBookmark(1L, CreateBookmarkRequest())
        }
    }

    @Test
    fun `createBookmark throws when both ids provided`() {
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

    @Test
    fun `createCustomBookmark saves with direct input`() {
        whenever(bookmarkedJobRepository.existsByUserIdAndUrl(1L, "https://custom.com")).thenReturn(false)
        whenever(bookmarkedJobRepository.save(any<BookmarkedJob>())).thenAnswer { it.arguments[0] }

        val result = bookmarkService.createCustomBookmark(
            1L,
            CreateCustomBookmarkRequest("프론트 개발자", "회사B", "https://custom.com", "React 필수"),
        )

        assertEquals("프론트 개발자", result.title)
        assertEquals("회사B", result.company)
        assertEquals("https://custom.com", result.url)
        assertEquals("React 필수", result.description)
        assertEquals(ApplicationStatus.NOT_APPLIED, result.status)
    }

    @Test
    fun `createCustomBookmark without url skips dedup check`() {
        whenever(bookmarkedJobRepository.save(any<BookmarkedJob>())).thenAnswer { it.arguments[0] }

        val result = bookmarkService.createCustomBookmark(
            1L,
            CreateCustomBookmarkRequest("디자이너", "회사C"),
        )

        assertEquals("디자이너", result.title)
        assertNull(result.url)
        verify(bookmarkedJobRepository, never()).existsByUserIdAndUrl(any(), any())
    }

    @Test
    fun `createCustomBookmark throws DuplicateBookmarkException for same url`() {
        whenever(bookmarkedJobRepository.existsByUserIdAndUrl(1L, "https://dup.com")).thenReturn(true)

        assertThrows<DuplicateBookmarkException> {
            bookmarkService.createCustomBookmark(
                1L,
                CreateCustomBookmarkRequest("개발자", "회사D", "https://dup.com"),
            )
        }
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

    @Test
    fun `createCustomBookmark throws when title exceeds 255 chars`() {
        assertThrows<IllegalArgumentException> {
            bookmarkService.createCustomBookmark(1L, CreateCustomBookmarkRequest("a".repeat(256), "회사"))
        }
    }

    @Test
    fun `createCustomBookmark throws when company exceeds 255 chars`() {
        assertThrows<IllegalArgumentException> {
            bookmarkService.createCustomBookmark(1L, CreateCustomBookmarkRequest("개발자", "a".repeat(256)))
        }
    }

    @Test
    fun `getBookmarks returns paginated list`() {
        val bookmark = BookmarkedJob(user, jobListing, "백엔드 개발자", "회사A", "https://a.com", "설명")
        whenever(bookmarkedJobRepository.findAllByUserIdOrderByBookmarkedAtDesc(1L, pageable))
            .thenReturn(PageImpl(listOf(bookmark), pageable, 1))

        val result = bookmarkService.getBookmarks(1L, null, pageable)

        assertEquals(1, result.totalElements)
        assertEquals("백엔드 개발자", result.content[0].title)
    }

    @Test
    fun `getBookmarks filters by status`() {
        val bookmark = BookmarkedJob(user, jobListing, "백엔드 개발자", "회사A", "https://a.com", "설명").apply {
            status = ApplicationStatus.APPLIED
        }
        whenever(
            bookmarkedJobRepository.findAllByUserIdAndStatusOrderByBookmarkedAtDesc(
                1L, ApplicationStatus.APPLIED, pageable,
            ),
        ).thenReturn(PageImpl(listOf(bookmark), pageable, 1))

        val result = bookmarkService.getBookmarks(1L, ApplicationStatus.APPLIED, pageable)

        assertEquals(1, result.totalElements)
        assertEquals(ApplicationStatus.APPLIED, result.content[0].status)
    }

    @Test
    fun `updateBookmark changes status and memo`() {
        val bookmark = BookmarkedJob(user, jobListing, "백엔드 개발자", "회사A", "https://a.com", "설명")
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
        val bookmark = BookmarkedJob(user, jobListing, "백엔드 개발자", "회사A", "https://a.com", "설명")
        whenever(bookmarkedJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(bookmark)

        assertThrows<IllegalArgumentException> {
            bookmarkService.updateBookmark(
                1L, 1L,
                UpdateBookmarkRequest(memo = "a".repeat(501)),
            )
        }
    }

    @Test
    fun `updateBookmark clears memo when empty string provided`() {
        val bookmark = BookmarkedJob(user, jobListing, "백엔드 개발자", "회사A", "https://a.com", "설명").apply {
            memo = "기존 메모"
        }
        whenever(bookmarkedJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(bookmark)

        val result = bookmarkService.updateBookmark(1L, 1L, UpdateBookmarkRequest(memo = ""))

        assertNull(result.memo)
    }

    @Test
    fun `updateBookmark returns 404 for other user bookmark`() {
        whenever(bookmarkedJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(null)

        assertThrows<NoSuchElementException> {
            bookmarkService.updateBookmark(1L, 1L, UpdateBookmarkRequest(status = ApplicationStatus.APPLIED))
        }
    }

    @Test
    fun `deleteBookmark deletes own bookmark`() {
        val bookmark = BookmarkedJob(user, jobListing, "백엔드 개발자", "회사A", "https://a.com", "설명")
        whenever(bookmarkedJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(bookmark)

        bookmarkService.deleteBookmark(1L, 1L)

        verify(bookmarkedJobRepository).delete(bookmark)
    }

    @Test
    fun `deleteBookmark returns 404 for other user bookmark`() {
        whenever(bookmarkedJobRepository.findByIdAndUserId(1L, 1L)).thenReturn(null)

        assertThrows<NoSuchElementException> {
            bookmarkService.deleteBookmark(1L, 1L)
        }
    }

    @Test
    fun `deleteBookmark throws NoSuchElementException for invalid id`() {
        whenever(bookmarkedJobRepository.findByIdAndUserId(999L, 1L)).thenReturn(null)

        assertThrows<NoSuchElementException> {
            bookmarkService.deleteBookmark(1L, 999L)
        }
    }
}
