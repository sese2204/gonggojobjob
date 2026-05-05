package org.example.kotlinai.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "ingestion_runs")
class IngestionRun(

    @Column(nullable = false)
    val sourceName: String,

    @Column(nullable = false)
    val startedAt: LocalDateTime,

    var completedAt: LocalDateTime? = null,

    var newCount: Int = 0,

    var duplicateCount: Int = 0,

    var failedCount: Int = 0,

    var deletedCount: Int = 0,

    var success: Boolean = false,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
