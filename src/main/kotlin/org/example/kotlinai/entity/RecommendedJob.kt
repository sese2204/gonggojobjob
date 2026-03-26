package org.example.kotlinai.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "recommended_jobs",
    indexes = [
        Index(name = "idx_recommended_job_search_id", columnList = "search_history_id"),
        Index(name = "idx_recommended_job_listing_id", columnList = "job_listing_id"),
    ]
)
class RecommendedJob(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_history_id", nullable = false)
    val searchHistory: SearchHistory,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "job_listing_id",
        nullable = false,
        foreignKey = ForeignKey(
            name = "fk_recommended_job_listing",
            foreignKeyDefinition = "FOREIGN KEY (job_listing_id) REFERENCES job_listings(id) ON DELETE CASCADE"
        )
    )
    val jobListing: JobListing,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val company: String,

    @Column(nullable = false)
    val url: String,

    @Column(nullable = false)
    val matchScore: Int,

    @Column(nullable = false, columnDefinition = "TEXT")
    val reason: String,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
