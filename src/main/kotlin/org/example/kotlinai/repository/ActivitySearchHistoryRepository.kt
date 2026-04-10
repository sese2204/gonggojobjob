package org.example.kotlinai.repository

import org.example.kotlinai.entity.ActivitySearchHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface ActivitySearchHistoryRepository : JpaRepository<ActivitySearchHistory, Long> {

    fun findAllByUserIdOrderBySearchedAtDesc(userId: Long, pageable: Pageable): Page<ActivitySearchHistory>

    fun countByUserIdAndSearchedAtAfter(userId: Long, after: LocalDateTime): Long
}
