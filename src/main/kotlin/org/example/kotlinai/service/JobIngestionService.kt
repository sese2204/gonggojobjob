package org.example.kotlinai.service

import org.example.kotlinai.dto.response.BackfillResponse
import org.example.kotlinai.dto.response.JobIngestionResponse
import org.example.kotlinai.entity.IngestionRun
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.repository.IngestionRunRepository
import org.example.kotlinai.repository.JobListingRepository
import org.example.kotlinai.service.EmbeddingService.Companion.toVectorString
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
@Transactional(readOnly = true)
class JobIngestionService(
    private val clients: List<ExternalJobClient>,
    private val jobListingRepository: JobListingRepository,
    private val ingestionRunRepository: IngestionRunRepository,
    private val embeddingService: EmbeddingService,
    private val jobListingSaver: JobListingSaver,
    private val embeddingUpdater: EmbeddingUpdater,
) {
    private val log = LoggerFactory.getLogger(JobIngestionService::class.java)

    fun getSourceNames(): List<String> = clients.map { it.sourceName() }

    fun runIngestion(source: String?): List<JobIngestionResponse> {
        val targets = if (source == null) {
            clients
        } else {
            require(clients.any { it.sourceName() == source }) {
                "알 수 없는 소스: '$source'. 사용 가능한 소스: ${getSourceNames()}"
            }
            clients.filter { it.sourceName() == source }
        }
        return targets.mapNotNull { client ->
            try {
                runSingle(client)
            } catch (e: Exception) {
                log.error("[Ingestion] {} 소스 실패, 다음 소스 진행: {}", client.sourceName(), e.message)
                null
            }
        }
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
        var connectionFailed = false
        for ((index, batch) in chunks.withIndex()) {
            if (connectionFailed) {
                failedCount += batch.size
                continue
            }

            val texts = batch.map { buildEmbeddingText(it) }
            var success = false
            var retries = 0
            val maxRetries = 3

            while (!success && retries <= maxRetries) {
                try {
                    val embeddings = embeddingService.embedTexts(texts)
                    batch.zip(embeddings).forEach { (listing, embedding) ->
                        embeddingUpdater.updateJobEmbedding(
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
                    if (isConnectionFailure(e)) {
                        log.error("[Backfill] DB 커넥션 실패 감지, 나머지 배치 건너뜀")
                        failedCount += batch.size
                        connectionFailed = true
                        break
                    }
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

            if (!connectionFailed && index < chunks.size - 1) {
                Thread.sleep(1000)
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

            val existingSourceIds = jobListingRepository
                .findSourceIdsBySourceName(client.sourceName())
                .toSet()

            val deduplicatedJobs = jobs.distinctBy { it.sourceId }
            val batchDuplicateCount = jobs.size - deduplicatedJobs.size
            if (batchDuplicateCount > 0) {
                log.info("배치 내 중복 sourceId {}건 제거", batchDuplicateCount)
            }

            val incomingSourceIds = deduplicatedJobs.mapNotNull { it.sourceId }.toSet()

            if (client.supportsFullSync()) {
                if (incomingSourceIds.isNotEmpty()) {
                    jobListingRepository.deleteBySourceNameAndSourceIdNotIn(
                        client.sourceName(),
                        incomingSourceIds.toList(),
                    )
                }
            } else {
                val reseenIds = incomingSourceIds.filter { it in existingSourceIds }
                if (reseenIds.isNotEmpty()) {
                    val refreshedDeadline = LocalDate.now().plusDays(DEFAULT_DEADLINE_DAYS)
                    reseenIds.chunked(500).forEach { chunk ->
                        jobListingRepository.refreshDeadlines(client.sourceName(), chunk, refreshedDeadline)
                    }
                    log.info("[{}] 기존 공고 {}건 deadline 갱신 → {}", client.sourceName(), reseenIds.size, refreshedDeadline)
                }
            }

            val newListings = mutableListOf<JobListing>()
            duplicateCount += batchDuplicateCount
            for (dto in deduplicatedJobs) {
                if (dto.sourceId in existingSourceIds) {
                    duplicateCount++
                    continue
                }

                try {
                    val listing = jobListingSaver.save(
                        JobListing(
                            title = stripHtml(dto.title),
                            company = stripHtml(dto.company),
                            url = dto.url,
                            description = dto.description?.let { stripHtml(it) },
                            sourceName = client.sourceName(),
                            sourceId = dto.sourceId,
                            deadline = parseDeadline(dto.deadline),
                        ),
                    )
                    newListings.add(listing)
                    newCount++
                } catch (e: Exception) {
                    log.warn("공고 저장 실패 (sourceId={}): {}", dto.sourceId, e.message)
                    failedCount++
                }
            }

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

    private fun parseDeadline(raw: String?): LocalDate {
        if (raw.isNullOrBlank()) return LocalDate.now().plusDays(DEFAULT_DEADLINE_DAYS)
        return try {
            val normalized = raw.replace(".", "-").replace("/", "-").trim()
            LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            LocalDate.now().plusDays(DEFAULT_DEADLINE_DAYS)
        }
    }

    companion object {
        private const val DEFAULT_DEADLINE_DAYS = 30L
    }

    private fun embedNewListings(listings: List<JobListing>) {
        if (listings.isEmpty()) return

        val embeddable = listings.filter { buildEmbeddingText(it).isNotBlank() }
        if (embeddable.isEmpty()) {
            log.warn("임베딩할 텍스트가 있는 공고가 없습니다 ({}건 스킵)", listings.size)
            return
        }

        for (batch in embeddable.chunked(10)) {
            val texts = batch.map { buildEmbeddingText(it) }
            try {
                val embeddings = embeddingService.embedTexts(texts)
                batch.zip(embeddings).forEach { (listing, embedding) ->
                    embeddingUpdater.updateJobEmbedding(
                        id = listing.id,
                        embedding = embedding.toVectorString(),
                        embeddedAt = LocalDateTime.now(),
                        embeddingModel = "gemini-embedding-001",
                    )
                }
            } catch (e: Exception) {
                log.warn("임베딩 생성 실패 ({}건), 나중에 backfill 가능: {}", batch.size, e.message)
                if (isConnectionFailure(e)) {
                    log.error("DB 커넥션 실패 감지, 나머지 배치 건너뜀 (backfill로 복구 가능)")
                    break
                }
            }
        }
    }

    private fun isConnectionFailure(e: Exception): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val msg = cause.message?.lowercase() ?: ""
            if (msg.contains("connection is closed") || msg.contains("connection reset") ||
                cause is java.net.SocketException
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun buildEmbeddingText(listing: JobListing): String =
        listOfNotNull(listing.title, listing.company, listing.description)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

private fun stripHtml(text: String): String =
    Jsoup.clean(text, Safelist.none()).trim()

fun IngestionRun.toResponse() = JobIngestionResponse(
    sourceName = sourceName,
    newCount = newCount,
    duplicateCount = duplicateCount,
    failedCount = failedCount,
    success = success,
)
