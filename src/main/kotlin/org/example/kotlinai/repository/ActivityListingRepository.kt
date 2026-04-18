package org.example.kotlinai.repository

import org.example.kotlinai.entity.ActivityListing
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ActivityListingRepository : JpaRepository<ActivityListing, Long> {

    @Query("SELECT a.sourceId FROM ActivityListing a WHERE a.sourceName = :sourceName")
    fun findSourceIdsBySourceName(@Param("sourceName") sourceName: String): List<String>

    @Modifying
    @Query("DELETE FROM ActivityListing a WHERE a.sourceName = :sourceName AND a.sourceId NOT IN :sourceIds")
    fun deleteBySourceNameAndSourceIdNotIn(
        @Param("sourceName") sourceName: String,
        @Param("sourceIds") sourceIds: List<String>,
    )

    // Vector similarity search — excludes expired listings
    @Query(
        value = """
            SELECT * FROM activity_listings
            WHERE embedding IS NOT NULL
              AND (deadline IS NULL OR deadline >= CURRENT_DATE)
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :lim
        """,
        nativeQuery = true,
    )
    fun findByVectorSimilarity(
        @Param("queryVector") queryVector: String,
        @Param("lim") limit: Int,
    ): List<ActivityListing>

    // Full-text keyword search — excludes expired listings
    @Query(
        value = """
            SELECT * FROM activity_listings
            WHERE search_vector @@ to_tsquery('simple', :query)
              AND (deadline IS NULL OR deadline >= CURRENT_DATE)
            ORDER BY ts_rank(search_vector, to_tsquery('simple', :query)) DESC
            LIMIT :lim
        """,
        nativeQuery = true,
    )
    fun findByKeyword(
        @Param("query") query: String,
        @Param("lim") limit: Int,
    ): List<ActivityListing>

    // Trigram fallback — excludes expired listings
    @Query(
        value = """
            SELECT * FROM activity_listings
            WHERE (title ILIKE :pattern OR description ILIKE :pattern)
              AND (deadline IS NULL OR deadline >= CURRENT_DATE)
            LIMIT :lim
        """,
        nativeQuery = true,
    )
    fun findByKeywordLike(
        @Param("pattern") pattern: String,
        @Param("lim") limit: Int,
    ): List<ActivityListing>

    fun countByCollectedAtAfter(after: java.time.LocalDateTime): Long

    fun findByEmbeddingIsNull(): List<ActivityListing>

    @Modifying
    @Query(
        value = """
            UPDATE activity_listings
            SET embedding = CAST(:embedding AS vector),
                embedded_at = :embeddedAt,
                embedding_model = :embeddingModel
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun updateEmbedding(
        @Param("id") id: Long,
        @Param("embedding") embedding: String,
        @Param("embeddedAt") embeddedAt: java.time.LocalDateTime,
        @Param("embeddingModel") embeddingModel: String,
    )

    @Modifying
    @Query("DELETE FROM ActivityListing a WHERE a.deadline < :cutoff")
    fun deleteExpired(@Param("cutoff") cutoff: LocalDate): Int

    fun countByDeadlineBefore(deadline: LocalDate): Long
}
