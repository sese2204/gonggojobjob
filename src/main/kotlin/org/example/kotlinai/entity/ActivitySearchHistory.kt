package org.example.kotlinai.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "activity_search_histories",
    indexes = [Index(name = "idx_activity_search_history_user_id", columnList = "user_id")],
)
class ActivitySearchHistory(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "tags_string")
    val tagsString: String? = null,

    val query: String? = null,

    @Column(nullable = false)
    val resultCount: Int,

    val hybridResultCount: Int? = null,

    val geminiInputChars: Int? = null,

    val latencyMs: Long? = null,

    @Column(nullable = false, updatable = false)
    val searchedAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "activitySearchHistory", cascade = [CascadeType.ALL], orphanRemoval = true)
    val recommendedActivities: MutableList<RecommendedActivity> = mutableListOf(),

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    val tags: List<String>
        get() = tagsString?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
}
