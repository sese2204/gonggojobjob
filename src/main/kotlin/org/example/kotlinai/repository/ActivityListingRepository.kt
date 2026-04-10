package org.example.kotlinai.repository

import org.example.kotlinai.entity.ActivityListing
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ActivityListingRepository : JpaRepository<ActivityListing, Long> {

    @Query("SELECT a.sourceId FROM ActivityListing a WHERE a.sourceName = :sourceName")
    fun findSourceIdsBySourceName(@Param("sourceName") sourceName: String): List<String>

    @Modifying
    @Query("DELETE FROM ActivityListing a WHERE a.sourceName = :sourceName AND a.sourceId NOT IN :sourceIds")
    fun deleteBySourceNameAndSourceIdNotIn(
        @Param("sourceName") sourceName: String,
        @Param("sourceIds") sourceIds: List<String>,
    )

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
}
