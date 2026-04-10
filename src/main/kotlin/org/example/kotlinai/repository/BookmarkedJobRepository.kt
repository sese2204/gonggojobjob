package org.example.kotlinai.repository

import org.example.kotlinai.entity.ApplicationStatus
import org.example.kotlinai.entity.BookmarkType
import org.example.kotlinai.entity.BookmarkedJob
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface BookmarkedJobRepository : JpaRepository<BookmarkedJob, Long> {

    fun findAllByUserIdOrderByBookmarkedAtDesc(userId: Long, pageable: Pageable): Page<BookmarkedJob>

    fun findAllByUserIdAndStatusOrderByBookmarkedAtDesc(
        userId: Long,
        status: ApplicationStatus,
        pageable: Pageable,
    ): Page<BookmarkedJob>

    fun findAllByUserIdAndTypeOrderByBookmarkedAtDesc(
        userId: Long,
        type: BookmarkType,
        pageable: Pageable,
    ): Page<BookmarkedJob>

    fun findAllByUserIdAndTypeAndStatusOrderByBookmarkedAtDesc(
        userId: Long,
        type: BookmarkType,
        status: ApplicationStatus,
        pageable: Pageable,
    ): Page<BookmarkedJob>

    fun findByIdAndUserId(id: Long, userId: Long): BookmarkedJob?

    fun existsByUserIdAndJobListingId(userId: Long, jobListingId: Long): Boolean

    fun existsByUserIdAndActivityListingId(userId: Long, activityListingId: Long): Boolean

    fun existsByUserIdAndUrl(userId: Long, url: String): Boolean
}
