package org.example.kotlinai.service

import org.example.kotlinai.dto.response.StatsResponse
import org.example.kotlinai.repository.JobListingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class StatsService(
    private val jobListingRepository: JobListingRepository,
) {

    fun getStats(): StatsResponse {
        val totalCount = jobListingRepository.count()
        val todayStart = LocalDate.now().atStartOfDay()
        val newTodayCount = jobListingRepository.countByCollectedAtAfter(todayStart)

        return StatsResponse(
            totalCount = totalCount,
            newTodayCount = newTodayCount,
        )
    }
}
