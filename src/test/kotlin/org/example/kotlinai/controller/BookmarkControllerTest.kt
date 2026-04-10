package org.example.kotlinai.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.example.kotlinai.dto.response.BookmarkResponse
import org.example.kotlinai.entity.ApplicationStatus
import org.example.kotlinai.entity.BookmarkType
import org.example.kotlinai.global.exception.DuplicateBookmarkException
import org.example.kotlinai.service.BookmarkService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.PageImpl
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.*
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class BookmarkControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var bookmarkService: BookmarkService

    private val objectMapper = jacksonObjectMapper().apply {
        findAndRegisterModules()
    }

    private val userAuth = UsernamePasswordAuthenticationToken(
        1L, null, listOf(SimpleGrantedAuthority("ROLE_USER")),
    )

    private val sampleBookmark = BookmarkResponse(
        id = 1L,
        type = BookmarkType.JOB,
        jobListingId = 10L,
        activityListingId = null,
        title = "백엔드 개발자",
        company = "회사A",
        url = "https://a.com",
        description = "Spring Boot 경험자 우대",
        category = null,
        startDate = null,
        endDate = null,
        status = ApplicationStatus.NOT_APPLIED,
        memo = null,
        bookmarkedAt = LocalDateTime.of(2026, 4, 8, 12, 0),
    )

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `POST bookmarks returns 201 with bookmark`() {
        whenever(bookmarkService.createBookmark(eq(1L), any())).thenReturn(sampleBookmark)

        mockMvc.post("/api/bookmarks") {
            with(authentication(userAuth))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("jobListingId" to 10L))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.title") { value("백엔드 개발자") }
            jsonPath("$.company") { value("회사A") }
            jsonPath("$.status") { value("NOT_APPLIED") }
        }
    }

    @Test
    fun `POST bookmarks returns 409 for duplicate`() {
        whenever(bookmarkService.createBookmark(eq(1L), any()))
            .thenThrow(DuplicateBookmarkException("이미 북마크된 공고입니다."))

        mockMvc.post("/api/bookmarks") {
            with(authentication(userAuth))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("jobListingId" to 10L))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.status") { value(409) }
        }
    }

    @Test
    fun `POST bookmarks custom returns 201`() {
        val customBookmark = sampleBookmark.copy(jobListingId = null, title = "프론트 개발자", company = "회사B")
        whenever(bookmarkService.createCustomBookmark(eq(1L), any())).thenReturn(customBookmark)

        mockMvc.post("/api/bookmarks/custom") {
            with(authentication(userAuth))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("title" to "프론트 개발자", "company" to "회사B", "url" to "https://b.com"),
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.title") { value("프론트 개발자") }
            jsonPath("$.jobListingId") { value(null as Any?) }
        }
    }

    @Test
    fun `GET bookmarks returns paginated list`() {
        whenever(bookmarkService.getBookmarks(eq(1L), eq(null), eq(null), any()))
            .thenReturn(PageImpl(listOf(sampleBookmark)))

        mockMvc.get("/api/bookmarks") {
            with(authentication(userAuth))
        }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].title") { value("백엔드 개발자") }
            jsonPath("$.content[0].status") { value("NOT_APPLIED") }
        }
    }

    @Test
    fun `GET bookmarks with status filter`() {
        val appliedBookmark = sampleBookmark.copy(status = ApplicationStatus.APPLIED)
        whenever(bookmarkService.getBookmarks(eq(1L), eq(null), eq(ApplicationStatus.APPLIED), any()))
            .thenReturn(PageImpl(listOf(appliedBookmark)))

        mockMvc.get("/api/bookmarks") {
            with(authentication(userAuth))
            param("status", "APPLIED")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].status") { value("APPLIED") }
        }
    }

    @Test
    fun `PATCH bookmarks updates status and memo`() {
        val updatedBookmark = sampleBookmark.copy(status = ApplicationStatus.INTERVIEWING, memo = "4/20 면접")
        whenever(bookmarkService.updateBookmark(eq(1L), eq(1L), any())).thenReturn(updatedBookmark)

        mockMvc.patch("/api/bookmarks/1") {
            with(authentication(userAuth))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("status" to "INTERVIEWING", "memo" to "4/20 면접"),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("INTERVIEWING") }
            jsonPath("$.memo") { value("4/20 면접") }
        }
    }

    @Test
    fun `PATCH bookmarks returns 404 for other user bookmark`() {
        whenever(bookmarkService.updateBookmark(eq(1L), eq(1L), any()))
            .thenThrow(NoSuchElementException("북마크를 찾을 수 없습니다."))

        mockMvc.patch("/api/bookmarks/1") {
            with(authentication(userAuth))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("status" to "APPLIED"))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.status") { value(404) }
        }
    }

    @Test
    fun `DELETE bookmarks returns 204`() {
        mockMvc.delete("/api/bookmarks/1") {
            with(authentication(userAuth))
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `DELETE bookmarks returns 404 for invalid id`() {
        whenever(bookmarkService.deleteBookmark(eq(1L), eq(999L)))
            .thenThrow(NoSuchElementException("북마크를 찾을 수 없습니다."))

        mockMvc.delete("/api/bookmarks/999") {
            with(authentication(userAuth))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.status") { value(404) }
        }
    }

    @Test
    fun `unauthenticated request returns 401`() {
        mockMvc.get("/api/bookmarks")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
