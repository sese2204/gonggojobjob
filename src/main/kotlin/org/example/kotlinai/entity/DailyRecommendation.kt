package org.example.kotlinai.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "daily_recommendations")
class DailyRecommendation(

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    val category: RecommendationCategory,

    @Column(name = "job_listing_id")
    val jobListingId: Long? = null,

    @Column(name = "activity_listing_id")
    val activityListingId: Long? = null,

    @Column(nullable = false)
    val title: String,

    val companyOrOrganizer: String? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    val url: String,

    val activityCategory: String? = null,

    val startDate: String? = null,

    val endDate: String? = null,

    val deadline: LocalDate? = null,

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
        require((jobListingId == null) != (activityListingId == null)) {
            "jobListingId 또는 activityListingId 중 정확히 하나만 설정해야 합니다."
        }
    }
}
