package org.example.kotlinai.service

import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.repository.JobListingRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class EmbeddingUpdater(
    private val jobListingRepository: JobListingRepository,
    private val activityListingRepository: ActivityListingRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updateJobEmbedding(id: Long, embedding: String, embeddedAt: LocalDateTime, embeddingModel: String) {
        jobListingRepository.updateEmbedding(id, embedding, embeddedAt, embeddingModel)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updateActivityEmbedding(id: Long, embedding: String, embeddedAt: LocalDateTime, embeddingModel: String) {
        activityListingRepository.updateEmbedding(id, embedding, embeddedAt, embeddingModel)
    }
}