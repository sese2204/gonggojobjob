package org.example.kotlinai.service

import org.example.kotlinai.entity.ActivityListing
import org.example.kotlinai.repository.ActivityListingRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ActivityListingSaver(
    private val activityListingRepository: ActivityListingRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun save(listing: ActivityListing): ActivityListing =
        activityListingRepository.save(listing)
}
