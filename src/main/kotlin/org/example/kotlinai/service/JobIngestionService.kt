package org.example.kotlinai.service

import org.example.kotlinai.dto.response.BackfillResponse
import org.example.kotlinai.dto.response.JobIngestionResponse
import org.example.kotlinai.entity.IngestionRun
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.repository.IngestionRunRepository
import org.example.kotlinai.repository.JobListingRepository
import org.example.kotlinai.service.EmbeddingService.Companion.toVectorString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class JobIngestionService(
    private val clients: List<ExternalJobClient>,
    private val jobListingRepository: JobListingRepository,
    private val ingestionRunRepository: IngestionRunRepository,
    private val embeddingService: EmbeddingService,
) {
    private val log = LoggerFactory.getLogger(JobIngestionService::class.java)

    fun getSourceNames(): List<String> = clients.map { it.sourceName() }

    @Transactional
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

    @Transactional
    fun backfillEmbeddings(): BackfillResponse {
        val unembedded = jobListingRepository.findByEmbeddingIsNull()
        if (unembedded.isEmpty()) {
            return BackfillResponse(processedCount = 0, failedCount = 0, totalUnembedded = 0)
        }

        var processedCount = 0
        var failedCount = 0

        val chunks = unembedded.chunked(10)
        chunks.forEachIndexed { index, batch ->
            val texts = batch.map { buildEmbeddingText(it) }
            var success = false
            var retries = 0
            val maxRetries = 3

            while (!success && retries <= maxRetries) {
                try {
                    val embeddings = embeddingService.embedTexts(texts)
                    batch.zip(embeddings).forEach { (listing, embedding) ->
                        jobListingRepository.updateEmbedding(
                            id = listing.id,
                            embedding = embedding.toVectorString(),
                            embeddedAt = LocalDateTime.now(),
                            embeddingModel = "gemini-embedding-001",
                        )
                        processedCount++
                    }
                    log.info("[Backfill] 배치 {}/{} 완료 ({}건)", index + 1, chunks.size, batch.size)
                    success = true
                } catch (e: Exception) {
                    retries++
                    if (retries > maxRetries) {
                        log.warn("[Backfill] 배치 {}/{} 최종 실패 ({}회 재시도): {}", index + 1, chunks.size, maxRetries, e.message)
                        failedCount += batch.size
                    } else {
                        val backoff = retries * 30_000L
                        log.info("[Backfill] 배치 {}/{} 429 발생, {}초 후 재시도 ({}/{})", index + 1, chunks.size, backoff / 1000, retries, maxRetries)
                        Thread.sleep(backoff)
                    }
                }
            }

            // Rate limit: Gemini free tier 100 req/min, 배치당 10건 소모 → 최대 10배치/분
            if (index < chunks.size - 1) {
                Thread.sleep(7000)
            }
        }

        val remainingUnembedded = jobListingRepository.findByEmbeddingIsNull().size
        return BackfillResponse(
            processedCount = processedCount,
            failedCount = failedCount,
            totalUnembedded = remainingUnembedded,
        )
    }

    private fun runSingle(client: ExternalJobClient): JobIngestionResponse {
        val run = IngestionRun(
            sourceName = client.sourceName(),
            startedAt = LocalDateTime.now(),
        )
        ingestionRunRepository.save(run)

        return try {
            val jobs = client.fetchJobs()
            var newCount = 0
            var duplicateCount = 0
            var failedCount = 0

            // Upsert: get existing sourceIds
            val existingSourceIds = jobListingRepository
                .findSourceIdsBySourceName(client.sourceName())
                .toSet()

            val incomingSourceIds = jobs.mapNotNull { it.sourceId }.toSet()

            // Delete obsolete listings (present in DB but absent from source)
            if (incomingSourceIds.isNotEmpty()) {
                jobListingRepository.deleteBySourceNameAndSourceIdNotIn(
                    client.sourceName(),
                    incomingSourceIds.toList(),
                )
            }

            // Insert new, skip existing
            val newListings = mutableListOf<JobListing>()
            for (dto in jobs) {
                if (dto.sourceId in existingSourceIds) {
                    duplicateCount++
                    continue
                }

                try {
                    val listing = jobListingRepository.save(
                        JobListing(
                            title = dto.title,
                            company = dto.company,
                            url = dto.url,
                            description = dto.description,
                            sourceName = client.sourceName(),
                            sourceId = dto.sourceId,
                        )
                    )
                    newListings.add(listing)
                    newCount++
                } catch (e: Exception) {
                    log.warn("공고 저장 실패 (sourceId={}): {}", dto.sourceId, e.message)
                    failedCount++
                }
            }

            // Auto-embed new listings
            embedNewListings(newListings)

            run.newCount = newCount
            run.duplicateCount = duplicateCount
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

    private fun embedNewListings(listings: List<JobListing>) {
        if (listings.isEmpty()) return

        // Filter out listings with empty text content
        val embeddable = listings.filter { buildEmbeddingText(it).isNotBlank() }
        if (embeddable.isEmpty()) {
            log.warn("임베딩할 텍스트가 있는 공고가 없습니다 ({}건 스킵)", listings.size)
            return
        }

        embeddable.chunked(10).forEach { batch ->
            val texts = batch.map { buildEmbeddingText(it) }
            try {
                val embeddings = embeddingService.embedTexts(texts)
                batch.zip(embeddings).forEach { (listing, embedding) ->
                    jobListingRepository.updateEmbedding(
                        id = listing.id,
                        embedding = embedding.toVectorString(),
                        embeddedAt = LocalDateTime.now(),
                        embeddingModel = "gemini-embedding-001",
                    )
                }
            } catch (e: Exception) {
                log.warn("임베딩 생성 실패 ({}건), 나중에 backfill 가능: {}", batch.size, e.message)
            }
        }
    }

    private fun buildEmbeddingText(listing: JobListing): String =
        listOfNotNull(listing.title, listing.company, listing.description)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

fun IngestionRun.toResponse() = JobIngestionResponse(
    sourceName = sourceName,
    newCount = newCount,
    duplicateCount = duplicateCount,
    failedCount = failedCount,
    success = success,
)
