package org.example.kotlinai.repository

import org.example.kotlinai.entity.JobListing
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface JobListingRepository : JpaRepository<JobListing, Long> {
    fun findTop10ByOrderByCollectedAtDesc(): List<JobListing>
    fun existsBySourceNameAndSourceId(sourceName: String, sourceId: String): Boolean
    fun deleteBySourceName(sourceName: String)

    // Vector similarity search (pgvector cosine distance)
    @Query(
        value = """
            SELECT * FROM job_listings
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :lim
        """,
        nativeQuery = true,
    )
    fun findByVectorSimilarity(
        @Param("queryVector") queryVector: String,
        @Param("lim") limit: Int,
    ): List<JobListing>

    // Full-text keyword search (tsvector)
    @Query(
        value = """
            SELECT * FROM job_listings
            WHERE search_vector @@ to_tsquery('simple', :query)
            ORDER BY ts_rank(search_vector, to_tsquery('simple', :query)) DESC
            LIMIT :lim
        """,
        nativeQuery = true,
    )
    fun findByKeyword(
        @Param("query") query: String,
        @Param("lim") limit: Int,
    ): List<JobListing>

    // Trigram fallback for substring matching
    @Query(
        value = """
            SELECT * FROM job_listings
            WHERE title ILIKE :pattern OR description ILIKE :pattern
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
    @Query("DELETE FROM JobListing j WHERE j.sourceName = :sourceName AND j.sourceId NOT IN :sourceIds")
    fun deleteBySourceNameAndSourceIdNotIn(
        @Param("sourceName") sourceName: String,
        @Param("sourceIds") sourceIds: List<String>,
    )

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
}
