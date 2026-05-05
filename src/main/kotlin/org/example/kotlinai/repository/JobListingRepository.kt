package org.example.kotlinai.repository

import org.example.kotlinai.entity.JobListing
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

interface JobListingRepository : JpaRepository<JobListing, Long> {
    fun findTop10ByOrderByCollectedAtDesc(): List<JobListing>
    fun existsBySourceNameAndSourceId(sourceName: String, sourceId: String): Boolean
    fun deleteBySourceName(sourceName: String)

    // Vector similarity search (pgvector cosine distance) — excludes expired listings
    @Query(
        value = """
            SELECT * FROM job_listings
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
    ): List<JobListing>

    // Full-text keyword search (tsvector) — excludes expired listings
    @Query(
        value = """
            SELECT * FROM job_listings
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
    ): List<JobListing>

    // Trigram fallback — excludes expired listings
    @Query(
        value = """
            SELECT * FROM job_listings
            WHERE (title ILIKE :pattern OR description ILIKE :pattern)
              AND (deadline IS NULL OR deadline >= CURRENT_DATE)
            LIMIT :lim
        """,
        nativeQuery = true,
    )
    fun findByKeywordLike(
        @Param("pattern") pattern: String,
        @Param("lim") limit: Int,
    ): List<JobListing>

    // Upsert support: get existing sourceIds for a source
    @Query("SELECT j.sourceId FROM JobListing j WHERE j.sourceName = :sourceName")
    fun findSourceIdsBySourceName(@Param("sourceName") sourceName: String): List<String>

    // Upsert support: delete obsolete listings
    @Modifying
    @Transactional
    @Query("DELETE FROM JobListing j WHERE j.sourceName = :sourceName AND j.sourceId NOT IN :sourceIds")
    fun deleteBySourceNameAndSourceIdNotIn(
        @Param("sourceName") sourceName: String,
        @Param("sourceIds") sourceIds: List<String>,
    )

    // Count jobs collected after a given time
    fun countByCollectedAtAfter(after: java.time.LocalDateTime): Long

    // Find unembedded listings for backfill
    fun findByEmbeddingIsNull(): List<JobListing>

    // Native update for embedding (bypasses Hibernate varchar→vector type mismatch)
    @Modifying
    @Query(
        value = """
            UPDATE job_listings
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
    @Transactional
    @Query(
        value = """
            UPDATE job_listings SET deadline = :deadline
            WHERE source_name = :sourceName AND source_id IN :sourceIds
        """,
        nativeQuery = true,
    )
    fun refreshDeadlines(
        @Param("sourceName") sourceName: String,
        @Param("sourceIds") sourceIds: List<String>,
        @Param("deadline") deadline: LocalDate,
    )

    @Modifying
    @Query("DELETE FROM JobListing j WHERE j.deadline < :cutoff")
    fun deleteExpired(@Param("cutoff") cutoff: LocalDate): Int

    fun countByDeadlineBefore(deadline: LocalDate): Long
}
