package org.example.kotlinai.repository

import org.example.kotlinai.entity.RecommendedActivity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RecommendedActivityRepository : JpaRepository<RecommendedActivity, Long> {

    @Query(
        value = "SELECT r FROM RecommendedActivity r WHERE r.activitySearchHistory.user.id = :userId",
        countQuery = "SELECT COUNT(r) FROM RecommendedActivity r WHERE r.activitySearchHistory.user.id = :userId",
    )
    fun findAllByUserId(
        @Param("userId") userId: Long,
        pageable: Pageable,
    ): Page<RecommendedActivity>

    fun findByIdAndActivitySearchHistoryUserId(id: Long, userId: Long): RecommendedActivity?
}
