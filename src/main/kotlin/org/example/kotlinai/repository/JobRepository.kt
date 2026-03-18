package org.example.kotlinai.repository

import org.example.kotlinai.entity.Job
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface JobRepository : JpaRepository<Job, Long> {
    fun findTop10ByOrderByCollectedAtDesc(): List<Job>
    fun countByCollectedAtAfter(since: LocalDateTime): Long
}
