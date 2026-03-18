package org.example.kotlinai.entity

import jakarta.persistence.*
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "job_listings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["source_name", "source_id"])],
)
class JobListing(

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val company: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val url: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Column(nullable = false)
    val collectedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "source_name")
    val sourceName: String? = null,

    @Column(name = "source_id")
    val sourceId: String? = null,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
