package org.example.kotlinai.repository

import org.example.kotlinai.entity.RecommendedJob
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RecommendedJobRepository : JpaRepository<RecommendedJob, Long> {

    @Query("SELECT r FROM RecommendedJob r WHERE r.searchHistory.user.id = :userId ORDER BY r.searchHistory.searchedAt DESC")
    fun findAllByUserId(
        @Param("userId") userId: Long,
        pageable: Pageable,
    ): Page<RecommendedJob>
}
