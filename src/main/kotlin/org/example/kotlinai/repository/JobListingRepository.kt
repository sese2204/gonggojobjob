package org.example.kotlinai.repository

import org.example.kotlinai.entity.JobListing
import org.springframework.data.jpa.repository.JpaRepository

interface JobListingRepository : JpaRepository<JobListing, Long> {
    fun findTop10ByOrderByCollectedAtDesc(): List<JobListing>
    fun existsBySourceNameAndSourceId(sourceName: String, sourceId: String): Boolean
    fun deleteBySourceName(sourceName: String)
}
