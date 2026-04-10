package org.example.kotlinai.service

import org.example.kotlinai.dto.request.CreateBookmarkRequest
import org.example.kotlinai.dto.request.CreateCustomBookmarkRequest
import org.example.kotlinai.dto.request.UpdateBookmarkRequest
import org.example.kotlinai.dto.response.BookmarkResponse
import org.example.kotlinai.entity.ApplicationStatus
import org.example.kotlinai.entity.BookmarkType
import org.example.kotlinai.entity.BookmarkedJob
import org.example.kotlinai.entity.User
import org.example.kotlinai.global.exception.DuplicateBookmarkException
import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.repository.BookmarkedJobRepository
import org.example.kotlinai.repository.JobListingRepository
import org.example.kotlinai.repository.RecommendedActivityRepository
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
    private val activityListingRepository: ActivityListingRepository,
    private val recommendedJobRepository: RecommendedJobRepository,
    private val recommendedActivityRepository: RecommendedActivityRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun createBookmark(userId: Long, request: CreateBookmarkRequest): BookmarkResponse {
        val ids = listOfNotNull(
            request.jobListingId, request.recommendedJobId,
            request.activityListingId, request.recommendedActivityId,
        )
        require(ids.size == 1) {
            "jobListingId, recommendedJobId, activityListingId, recommendedActivityId 중 정확히 하나를 입력해주세요."
        }

        val user = userRepository.getReferenceById(userId)

        return when {
            request.jobListingId != null -> createFromJobListing(userId, user, request.jobListingId)
            request.recommendedJobId != null -> createFromRecommendedJob(userId, user, request.recommendedJobId)
            request.activityListingId != null -> createFromActivityListing(userId, user, request.activityListingId)
            else -> createFromRecommendedActivity(userId, user, request.recommendedActivityId!!)
        }
    }

    @Transactional
    fun createCustomBookmark(userId: Long, request: CreateCustomBookmarkRequest): BookmarkResponse {
        require(request.title.isNotBlank()) { "제목을 입력해주세요." }
        require(request.title.length <= 255) { "제목은 255자 이내로 입력해주세요." }
        require(request.company.isNotBlank()) { "회사명/주최를 입력해주세요." }
        require(request.company.length <= 255) { "회사명/주최는 255자 이내로 입력해주세요." }
        request.url?.let { require(it.length <= 2048) { "URL은 2048자 이내로 입력해주세요." } }

        val user = userRepository.getReferenceById(userId)

        if (!request.url.isNullOrBlank() && bookmarkedJobRepository.existsByUserIdAndUrl(userId, request.url)) {
            throw DuplicateBookmarkException("이미 북마크된 항목입니다. url=${request.url}")
        }

        val bookmarkedJob = bookmarkedJobRepository.save(
            BookmarkedJob(
                user = user,
                type = request.type,
                title = request.title.trim(),
                company = request.company.trim(),
                url = request.url?.trim()?.ifBlank { null },
                description = request.description?.trim(),
                category = request.category?.trim(),
                startDate = request.startDate?.trim(),
                endDate = request.endDate?.trim(),
            ),
        )
        return bookmarkedJob.toResponse()
    }

    fun getBookmarks(
        userId: Long,
        type: BookmarkType?,
        status: ApplicationStatus?,
        pageable: Pageable,
    ): Page<BookmarkResponse> =
        when {
            type != null && status != null ->
                bookmarkedJobRepository.findAllByUserIdAndTypeAndStatusOrderByBookmarkedAtDesc(userId, type, status, pageable)
            type != null ->
                bookmarkedJobRepository.findAllByUserIdAndTypeOrderByBookmarkedAtDesc(userId, type, pageable)
            status != null ->
                bookmarkedJobRepository.findAllByUserIdAndStatusOrderByBookmarkedAtDesc(userId, status, pageable)
            else ->
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
                type = BookmarkType.JOB,
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
                type = BookmarkType.JOB,
                jobListing = jobListing,
                title = recommendedJob.title,
                company = recommendedJob.company,
                url = recommendedJob.url,
                description = jobListing?.description,
            ),
        )
        return bookmarkedJob.toResponse()
    }

    private fun createFromActivityListing(userId: Long, user: User, activityListingId: Long): BookmarkResponse {
        if (bookmarkedJobRepository.existsByUserIdAndActivityListingId(userId, activityListingId)) {
            throw DuplicateBookmarkException("이미 북마크된 활동입니다. activityListingId=$activityListingId")
        }

        val activityListing = activityListingRepository.findById(activityListingId)
            .orElseThrow { NoSuchElementException("활동을 찾을 수 없습니다. id=$activityListingId") }

        val bookmarkedJob = bookmarkedJobRepository.save(
            BookmarkedJob(
                user = user,
                type = BookmarkType.ACTIVITY,
                activityListing = activityListing,
                title = activityListing.title,
                company = activityListing.organizer,
                url = activityListing.url,
                description = activityListing.description,
                category = activityListing.category,
                startDate = activityListing.startDate,
                endDate = activityListing.endDate,
            ),
        )
        return bookmarkedJob.toResponse()
    }

    private fun createFromRecommendedActivity(userId: Long, user: User, recommendedActivityId: Long): BookmarkResponse {
        val recommendedActivity = recommendedActivityRepository.findById(recommendedActivityId)
            .orElseThrow { NoSuchElementException("추천 활동을 찾을 수 없습니다. id=$recommendedActivityId") }

        val activityListingId = recommendedActivity.activityListing.id.takeIf { it != 0L }

        if (activityListingId != null && bookmarkedJobRepository.existsByUserIdAndActivityListingId(userId, activityListingId)) {
            throw DuplicateBookmarkException("이미 북마크된 활동입니다. activityListingId=$activityListingId")
        }

        if (recommendedActivity.url.isNotBlank() && bookmarkedJobRepository.existsByUserIdAndUrl(userId, recommendedActivity.url)) {
            throw DuplicateBookmarkException("이미 북마크된 활동입니다. url=${recommendedActivity.url}")
        }

        val activityListing = activityListingId?.let { activityListingRepository.findById(it).orElse(null) }

        val bookmarkedJob = bookmarkedJobRepository.save(
            BookmarkedJob(
                user = user,
                type = BookmarkType.ACTIVITY,
                activityListing = activityListing,
                title = recommendedActivity.title,
                company = recommendedActivity.organizer,
                url = recommendedActivity.url,
                description = activityListing?.description,
                category = recommendedActivity.category,
                startDate = recommendedActivity.startDate,
                endDate = recommendedActivity.endDate,
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
    type = type,
    jobListingId = jobListing?.id,
    activityListingId = activityListing?.id,
    title = title,
    company = company,
    url = url,
    description = description,
    category = category,
    startDate = startDate,
    endDate = endDate,
    status = status,
    memo = memo,
    bookmarkedAt = bookmarkedAt,
)
