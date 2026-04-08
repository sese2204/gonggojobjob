package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.request.CreateBookmarkRequest
import org.example.kotlinai.dto.request.CreateCustomBookmarkRequest
import org.example.kotlinai.dto.request.UpdateBookmarkRequest
import org.example.kotlinai.dto.response.BookmarkResponse
import org.example.kotlinai.entity.ApplicationStatus
import org.example.kotlinai.global.exception.ErrorResponse
import org.example.kotlinai.service.BookmarkService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Bookmark", description = "공고 북마크 및 지원 상태 관리 API")
@RestController
@RequestMapping("/api/bookmarks")
class BookmarkController(
    private val bookmarkService: BookmarkService,
) {

    @Operation(
        summary = "공고 북마크 생성",
        description = "jobListingId 또는 recommendedJobId로 공고를 북마크합니다. 스냅샷이 저장되어 원본 삭제 후에도 유지됩니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "북마크 생성 성공"),
        ApiResponse(
            responseCode = "404",
            description = "공고를 찾을 수 없음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "이미 북마크된 공고",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBookmark(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreateBookmarkRequest,
    ): BookmarkResponse = bookmarkService.createBookmark(userId, request)

    @Operation(
        summary = "커스텀 공고 북마크 생성",
        description = "외부에서 발견한 공고를 직접 입력하여 북마크합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "커스텀 북마크 생성 성공"),
        ApiResponse(
            responseCode = "409",
            description = "같은 URL의 북마크가 이미 존재",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCustomBookmark(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreateCustomBookmarkRequest,
    ): BookmarkResponse = bookmarkService.createCustomBookmark(userId, request)

    @Operation(
        summary = "북마크 목록 조회",
        description = "사용자의 북마크 목록을 최신순으로 조회합니다. status 파라미터로 필터링 가능합니다.",
    )
    @GetMapping
    fun getBookmarks(
        @AuthenticationPrincipal userId: Long,
        @RequestParam(required = false) status: ApplicationStatus?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<BookmarkResponse> = bookmarkService.getBookmarks(userId, status, pageable)

    @Operation(
        summary = "북마크 수정",
        description = "북마크의 지원 상태 및 메모를 수정합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "수정 성공"),
        ApiResponse(
            responseCode = "403",
            description = "접근 권한 없음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "북마크를 찾을 수 없음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PatchMapping("/{id}")
    fun updateBookmark(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateBookmarkRequest,
    ): BookmarkResponse = bookmarkService.updateBookmark(userId, id, request)

    @Operation(
        summary = "북마크 삭제",
        description = "북마크를 삭제합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 성공"),
        ApiResponse(
            responseCode = "403",
            description = "접근 권한 없음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "북마크를 찾을 수 없음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBookmark(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    ) = bookmarkService.deleteBookmark(userId, id)
}
