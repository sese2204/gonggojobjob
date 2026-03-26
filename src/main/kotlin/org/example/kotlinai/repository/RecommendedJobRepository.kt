package org.example.kotlinai.repository

import org.example.kotlinai.entity.RecommendedJob
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface RecommendedJobRepository : JpaRepository<RecommendedJob, Long> {

    fun findAllBySearchHistoryUserIdOrderBySearchHistorySearchedAtDesc(
        userId: Long,
        pageable: Pageable,
    ): Page<RecommendedJob>
}
