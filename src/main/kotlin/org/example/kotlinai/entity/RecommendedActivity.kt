package org.example.kotlinai.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "recommended_activities",
    indexes = [
        Index(name = "idx_recommended_activity_search_id", columnList = "activity_search_history_id"),
        Index(name = "idx_recommended_activity_listing_id", columnList = "activity_listing_id"),
    ],
)
class RecommendedActivity(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_search_history_id", nullable = false)
    val activitySearchHistory: ActivitySearchHistory,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "activity_listing_id",
        nullable = false,
        foreignKey = ForeignKey(
            name = "fk_recommended_activity_listing",
            foreignKeyDefinition = "FOREIGN KEY (activity_listing_id) REFERENCES activity_listings(id) ON DELETE CASCADE",
        ),
    )
    val activityListing: ActivityListing,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val organizer: String,

    @Column(nullable = false)
    val url: String,

    val category: String? = null,

    val startDate: String? = null,

    val endDate: String? = null,

    @Column(nullable = false)
    val matchScore: Int,

    @Column(nullable = false, columnDefinition = "TEXT")
    val reason: String,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
