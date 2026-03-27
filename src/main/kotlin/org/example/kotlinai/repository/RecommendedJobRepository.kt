package org.example.kotlinai.repository

import org.example.kotlinai.entity.RecommendedJob
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RecommendedJobRepository : JpaRepository<RecommendedJob, Long> {

    @Query(
        value = "SELECT r FROM RecommendedJob r WHERE r.searchHistory.user.id = :userId",
        countQuery = "SELECT COUNT(r) FROM RecommendedJob r WHERE r.searchHistory.user.id = :userId",
    )
    fun findAllByUserId(
        @Param("userId") userId: Long,
        pageable: Pageable,
    ): Page<RecommendedJob>

    fun findByIdAndSearchHistoryUserId(id: Long, userId: Long): RecommendedJob?
}
