package org.example.kotlinai.service

import org.example.kotlinai.dto.response.StatsResponse
import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.repository.JobListingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class StatsService(
    private val jobListingRepository: JobListingRepository,
    private val activityListingRepository: ActivityListingRepository,
) {

    fun getStats(): StatsResponse {
        val todayStart = LocalDate.now().atStartOfDay()

        return StatsResponse(
            totalCount = jobListingRepository.count(),
            newTodayCount = jobListingRepository.countByCollectedAtAfter(todayStart),
            activityTotalCount = activityListingRepository.count(),
            activityNewTodayCount = activityListingRepository.countByCollectedAtAfter(todayStart),
        )
    }
}
