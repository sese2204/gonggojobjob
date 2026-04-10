package org.example.kotlinai.repository

import org.example.kotlinai.entity.DailyRecommendation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface DailyRecommendationRepository : JpaRepository<DailyRecommendation, Long> {

    fun findByGeneratedAtOrderByCategoryAscMatchScoreDesc(generatedAt: LocalDate): List<DailyRecommendation>

    fun deleteByGeneratedAt(generatedAt: LocalDate)

    fun countByGeneratedAt(generatedAt: LocalDate): Long

    @Query("SELECT MAX(d.generatedAt) FROM DailyRecommendation d")
    fun findLatestGeneratedAt(): LocalDate?
}
