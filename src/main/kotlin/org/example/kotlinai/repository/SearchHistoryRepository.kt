package org.example.kotlinai.repository

import org.example.kotlinai.entity.SearchHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface SearchHistoryRepository : JpaRepository<SearchHistory, Long> {

    fun findAllByUserIdOrderBySearchedAtDesc(userId: Long, pageable: Pageable): Page<SearchHistory>

    fun countByUserIdAndSearchedAtAfter(userId: Long, after: LocalDateTime): Long
}
