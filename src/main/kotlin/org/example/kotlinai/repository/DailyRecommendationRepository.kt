package org.example.kotlinai.repository

import org.example.kotlinai.entity.DailyRecommendation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface DailyRecommendationRepository : JpaRepository<DailyRecommendation, Long> {

    @Query("""
        SELECT d FROM DailyRecommendation d
        WHERE d.generatedAt = :generatedAt
          AND (d.deadline IS NULL OR d.deadline >= :today)
        ORDER BY d.category ASC, d.matchScore DESC
    """)
    fun findByGeneratedAtOrderByCategoryAscMatchScoreDesc(
        @Param("generatedAt") generatedAt: LocalDate,
        @Param("today") today: LocalDate,
    ): List<DailyRecommendation>

    fun deleteByGeneratedAt(generatedAt: LocalDate)

    fun countByGeneratedAt(generatedAt: LocalDate): Long

    @Query("SELECT MAX(d.generatedAt) FROM DailyRecommendation d")
    fun findLatestGeneratedAt(): LocalDate?

    @Modifying
    @Query("DELETE FROM DailyRecommendation d WHERE d.generatedAt < :cutoff")
    fun deleteByGeneratedAtBefore(@Param("cutoff") cutoff: LocalDate): Int
}
