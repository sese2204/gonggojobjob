package org.example.kotlinai.service

import org.example.kotlinai.dto.response.JobIngestionResponse
import org.example.kotlinai.entity.IngestionRun
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.repository.IngestionRunRepository
import org.example.kotlinai.repository.JobListingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class JobIngestionService(
    private val clients: List<ExternalJobClient>,
    private val jobListingRepository: JobListingRepository,
    private val ingestionRunRepository: IngestionRunRepository,
) {

    fun getSourceNames(): List<String> = clients.map { it.sourceName() }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun runIngestion(source: String?): List<JobIngestionResponse> {
        val targets = if (source == null) {
            clients
        } else {
            require(clients.any { it.sourceName() == source }) {
                "알 수 없는 소스: '$source'. 사용 가능한 소스: ${getSourceNames()}"
            }
            clients.filter { it.sourceName() == source }
        }
        return targets.map { runSingle(it) }
    }

    fun getHistory(): List<JobIngestionResponse> =
        ingestionRunRepository.findTop10ByOrderByStartedAtDesc().map { it.toResponse() }

    private fun runSingle(client: ExternalJobClient): JobIngestionResponse {
        val run = IngestionRun(
            sourceName = client.sourceName(),
            startedAt = LocalDateTime.now(),
        )
        ingestionRunRepository.save(run)

        return try {
            val jobs = client.fetchJobs()
            var newCount = 0
            var failedCount = 0

            jobListingRepository.deleteBySourceName(client.sourceName())

            for (dto in jobs) {
                try {
                    jobListingRepository.save(
                        JobListing(
                            title = dto.title,
                            company = dto.company,
                            url = dto.url,
                            description = dto.description,
                            sourceName = client.sourceName(),
                            sourceId = dto.sourceId,
                        )
                    )
                    newCount++
                } catch (e: Exception) {
                    failedCount++
                }
            }

            run.newCount = newCount
            run.duplicateCount = 0
            run.failedCount = failedCount
            run.success = true
            run.completedAt = LocalDateTime.now()
            ingestionRunRepository.save(run)

            run.toResponse()
        } catch (e: Exception) {
            run.success = false
            run.completedAt = LocalDateTime.now()
            ingestionRunRepository.save(run)
            throw e
        }
    }
}

fun IngestionRun.toResponse() = JobIngestionResponse(
    sourceName = sourceName,
    newCount = newCount,
    duplicateCount = duplicateCount,
    failedCount = failedCount,
    success = success,
)
