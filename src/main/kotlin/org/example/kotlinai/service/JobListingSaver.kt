package org.example.kotlinai.service

import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.repository.JobListingRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class JobListingSaver(
    private val jobListingRepository: JobListingRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun save(listing: JobListing): JobListing =
        jobListingRepository.save(listing)
}
