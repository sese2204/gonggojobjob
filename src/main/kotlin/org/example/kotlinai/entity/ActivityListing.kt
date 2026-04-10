package org.example.kotlinai.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "activity_listings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["source_name", "source_id"])],
)
class ActivityListing(

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val organizer: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val url: String,

    @Column(columnDefinition = "TEXT")
    val category: String? = null,

    val startDate: String? = null,

    val endDate: String? = null,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Column(nullable = false)
    val collectedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "source_name", nullable = false)
    val sourceName: String,

    @Column(name = "source_id", nullable = false)
    val sourceId: String,

    @Column(insertable = false, updatable = false, columnDefinition = "vector(768)")
    var embedding: String? = null,

    var embeddedAt: LocalDateTime? = null,

    var embeddingModel: String? = null,

    @Column(insertable = false, updatable = false, columnDefinition = "tsvector")
    val searchVector: String? = null,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
