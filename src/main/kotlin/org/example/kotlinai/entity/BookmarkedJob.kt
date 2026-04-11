package org.example.kotlinai.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "bookmarked_jobs",
    indexes = [
        Index(name = "idx_bookmarked_job_user_id", columnList = "user_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_bookmarked_job_user_listing",
            columnNames = ["user_id", "job_listing_id"],
        ),
    ],
)
class BookmarkedJob(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    val type: BookmarkType = BookmarkType.JOB,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "job_listing_id",
        foreignKey = ForeignKey(
            name = "fk_bookmarked_job_listing",
            foreignKeyDefinition = "FOREIGN KEY (job_listing_id) REFERENCES job_listings(id) ON DELETE SET NULL",
        ),
    )
    val jobListing: JobListing? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "activity_listing_id",
        foreignKey = ForeignKey(
            name = "fk_bookmarked_activity_listing",
            foreignKeyDefinition = "FOREIGN KEY (activity_listing_id) REFERENCES activity_listings(id) ON DELETE SET NULL",
        ),
    )
    val activityListing: ActivityListing? = null,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val company: String,

    @Column(columnDefinition = "TEXT")
    val url: String? = null,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    val category: String? = null,

    val startDate: String? = null,

    val endDate: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    var status: ApplicationStatus = ApplicationStatus.NOT_APPLIED,

    @Column(length = 500)
    var memo: String? = null,

    @Column(nullable = false, updatable = false)
    val bookmarkedAt: LocalDateTime = LocalDateTime.now(),

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
