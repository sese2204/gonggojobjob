package org.example.kotlinai.service

import org.example.kotlinai.entity.Job
import org.example.kotlinai.repository.JobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class JobIngestionService(
    private val externalJobClient: ExternalJobClient,
    private val jobRepository: JobRepository,
) {

    @Transactional
    fun ingestJobs(): Int {
        val externalJobs = externalJobClient.fetchJobs()
        val entities = externalJobs.map { dto ->
            Job(title = dto.title, company = dto.company, url = dto.url)
        }
        jobRepository.saveAll(entities)
        return entities.size
    }
}
