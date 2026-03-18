package org.example.kotlinai.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "jobs")
class Job(

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val company: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val url: String,

    @Column(nullable = false, updatable = false)
    val collectedAt: LocalDateTime = LocalDateTime.now(),

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
