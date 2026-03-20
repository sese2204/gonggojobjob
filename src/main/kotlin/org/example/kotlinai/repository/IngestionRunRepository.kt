package org.example.kotlinai.repository

import org.example.kotlinai.entity.IngestionRun
import org.springframework.data.jpa.repository.JpaRepository

interface IngestionRunRepository : JpaRepository<IngestionRun, Long> {
    fun findTop10ByOrderByStartedAtDesc(): List<IngestionRun>
}
