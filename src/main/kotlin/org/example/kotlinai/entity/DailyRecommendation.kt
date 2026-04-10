package org.example.kotlinai.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "daily_recommendations")
class DailyRecommendation(

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val category: RecommendationCategory,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "job_listing_id",
        foreignKey = ForeignKey(
            name = "fk_daily_rec_job_listing",
            foreignKeyDefinition = "FOREIGN KEY (job_listing_id) REFERENCES job_listings(id) ON DELETE CASCADE",
        ),
    )
    val jobListing: JobListing? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "activity_listing_id",
        foreignKey = ForeignKey(
            name = "fk_daily_rec_activity_listing",
            foreignKeyDefinition = "FOREIGN KEY (activity_listing_id) REFERENCES activity_listings(id) ON DELETE CASCADE",
        ),
    )
    val activityListing: ActivityListing? = null,

    @Column(nullable = false)
    val title: String,

    val companyOrOrganizer: String? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    val url: String,

    val activityCategory: String? = null,

    val startDate: String? = null,

    val endDate: String? = null,

    @Column(nullable = false)
    val matchScore: Int,

    @Column(nullable = false, columnDefinition = "TEXT")
    val reason: String,

    @Column(nullable = false)
    val generatedAt: LocalDate,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    init {
        require((jobListing == null) != (activityListing == null)) {
            "jobListing 또는 activityListing 중 정확히 하나만 설정해야 합니다."
        }
    }
}
