package org.example.kotlinai.service

import org.example.kotlinai.dto.request.CreateBookmarkRequest
import org.example.kotlinai.dto.request.CreateCustomBookmarkRequest
import org.example.kotlinai.dto.request.UpdateBookmarkRequest
import org.example.kotlinai.dto.response.BookmarkResponse
import org.example.kotlinai.entity.ApplicationStatus
import org.example.kotlinai.entity.BookmarkedJob
import org.example.kotlinai.entity.User
import org.example.kotlinai.global.exception.DuplicateBookmarkException
import org.example.kotlinai.repository.BookmarkedJobRepository
import org.example.kotlinai.repository.JobListingRepository
import org.example.kotlinai.repository.RecommendedJobRepository
import org.example.kotlinai.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BookmarkService(
    private val bookmarkedJobRepository: BookmarkedJobRepository,
    private val jobListingRepository: JobListingRepository,
    private val recommendedJobRepository: RecommendedJobRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun createBookmark(userId: Long, request: CreateBookmarkRequest): BookmarkResponse {
        require(request.jobListingId != null || request.recommendedJobId != null) {
            "jobListingId 또는 recommendedJobId 중 하나를 입력해주세요."
        }
        require(request.jobListingId == null || request.recommendedJobId == null) {
            "jobListingId와 recommendedJobId를 동시에 입력할 수 없습니다."
        }

        val user = userRepository.getReferenceById(userId)

        return if (request.jobListingId != null) {
            createFromJobListing(userId, user, request.jobListingId)
        } else {
            createFromRecommendedJob(userId, user, request.recommendedJobId!!)
        }
    }

    @Transactional
    fun createCustomBookmark(userId: Long, request: CreateCustomBookmarkRequest): BookmarkResponse {
        require(request.title.isNotBlank()) { "제목을 입력해주세요." }
        require(request.title.length <= 255) { "제목은 255자 이내로 입력해주세요." }
        require(request.company.isNotBlank()) { "회사명을 입력해주세요." }
        require(request.company.length <= 255) { "회사명은 255자 이내로 입력해주세요." }
        request.url?.let { require(it.length <= 2048) { "URL은 2048자 이내로 입력해주세요." } }

        val user = userRepository.getReferenceById(userId)

        if (!request.url.isNullOrBlank() && bookmarkedJobRepository.existsByUserIdAndUrl(userId, request.url)) {
            throw DuplicateBookmarkException("이미 북마크된 공고입니다. url=${request.url}")
        }

        val bookmarkedJob = bookmarkedJobRepository.save(
            BookmarkedJob(
                user = user,
                title = request.title.trim(),
                company = request.company.trim(),
                url = request.url?.trim()?.ifBlank { null },
                description = request.description?.trim(),
            ),
        )
        return bookmarkedJob.toResponse()
    }

    fun getBookmarks(userId: Long, status: ApplicationStatus?, pageable: Pageable): Page<BookmarkResponse> =
        if (status != null) {
            bookmarkedJobRepository.findAllByUserIdAndStatusOrderByBookmarkedAtDesc(userId, status, pageable)
        } else {
            bookmarkedJobRepository.findAllByUserIdOrderByBookmarkedAtDesc(userId, pageable)
        }.map { it.toResponse() }

    @Transactional
    fun updateBookmark(userId: Long, bookmarkId: Long, request: UpdateBookmarkRequest): BookmarkResponse {
        val bookmark = findOwnedBookmark(userId, bookmarkId)

        request.status?.let { bookmark.status = it }
        request.memo?.let {
            require(it.length <= 500) { "메모는 500자 이내로 입력해주세요." }
            bookmark.memo = it.ifBlank { null }
        }

        return bookmark.toResponse()
    }

    @Transactional
    fun deleteBookmark(userId: Long, bookmarkId: Long) {
        val bookmark = findOwnedBookmark(userId, bookmarkId)
        bookmarkedJobRepository.delete(bookmark)
    }

    private fun createFromJobListing(userId: Long, user: User, jobListingId: Long): BookmarkResponse {
        if (bookmarkedJobRepository.existsByUserIdAndJobListingId(userId, jobListingId)) {
            throw DuplicateBookmarkException("이미 북마크된 공고입니다. jobListingId=$jobListingId")
        }

        val jobListing = jobListingRepository.findById(jobListingId)
            .orElseThrow { NoSuchElementException("공고를 찾을 수 없습니다. id=$jobListingId") }

        val bookmarkedJob = bookmarkedJobRepository.save(
            BookmarkedJob(
                user = user,
                jobListing = jobListing,
                title = jobListing.title,
                company = jobListing.company,
                url = jobListing.url,
                description = jobListing.description,
            ),
        )
        return bookmarkedJob.toResponse()
    }

    private fun createFromRecommendedJob(userId: Long, user: User, recommendedJobId: Long): BookmarkResponse {
        val recommendedJob = recommendedJobRepository.findById(recommendedJobId)
            .orElseThrow { NoSuchElementException("추천 공고를 찾을 수 없습니다. id=$recommendedJobId") }

        val jobListingId = recommendedJob.jobListing.id.takeIf { it != 0L }

        if (jobListingId != null && bookmarkedJobRepository.existsByUserIdAndJobListingId(userId, jobListingId)) {
            throw DuplicateBookmarkException("이미 북마크된 공고입니다. jobListingId=$jobListingId")
        }

        if (recommendedJob.url.isNotBlank() && bookmarkedJobRepository.existsByUserIdAndUrl(userId, recommendedJob.url)) {
            throw DuplicateBookmarkException("이미 북마크된 공고입니다. url=${recommendedJob.url}")
        }

        val jobListing = jobListingId?.let { jobListingRepository.findById(it).orElse(null) }

        val bookmarkedJob = bookmarkedJobRepository.save(
            BookmarkedJob(
                user = user,
                jobListing = jobListing,
                title = recommendedJob.title,
                company = recommendedJob.company,
                url = recommendedJob.url,
                description = jobListing?.description,
            ),
        )
        return bookmarkedJob.toResponse()
    }

    private fun findOwnedBookmark(userId: Long, bookmarkId: Long): BookmarkedJob =
        bookmarkedJobRepository.findByIdAndUserId(bookmarkId, userId)
            ?: throw NoSuchElementException("북마크를 찾을 수 없습니다. id=$bookmarkId")
}

private fun BookmarkedJob.toResponse() = BookmarkResponse(
    id = id,
    jobListingId = jobListing?.id,
    title = title,
    company = company,
    url = url,
    description = description,
    status = status,
    memo = memo,
    bookmarkedAt = bookmarkedAt,
)
