package org.example.kotlinai.service

import org.example.kotlinai.dto.response.ActivityBackfillResponse
import org.example.kotlinai.dto.response.ActivityIngestionResponse
import org.example.kotlinai.entity.ActivityListing
import org.example.kotlinai.entity.IngestionRun
import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.repository.IngestionRunRepository
import org.example.kotlinai.service.EmbeddingService.Companion.toVectorString
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ActivityIngestionService(
    private val clients: List<ExternalActivityClient>,
    private val activityListingRepository: ActivityListingRepository,
    private val ingestionRunRepository: IngestionRunRepository,
    private val embeddingService: EmbeddingService,
    private val activityListingSaver: ActivityListingSaver,
) {
    private val log = LoggerFactory.getLogger(ActivityIngestionService::class.java)
    private val activitySourceNames by lazy { clients.map { it.sourceName() }.toSet() }

    fun getSourceNames(): List<String> = clients.map { it.sourceName() }

    @Transactional
    fun runIngestion(source: String?): List<ActivityIngestionResponse> {
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

    fun getHistory(): List<ActivityIngestionResponse> =
        ingestionRunRepository.findTop10BySourceNameInOrderByStartedAtDesc(activitySourceNames)
            .map { it.toActivityResponse() }

    fun backfillEmbeddings(): ActivityBackfillResponse {
        val unembedded = activityListingRepository.findByEmbeddingIsNull()
        if (unembedded.isEmpty()) {
            return ActivityBackfillResponse(processedCount = 0, failedCount = 0, totalUnembedded = 0)
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
                        activityListingRepository.updateEmbedding(
                            id = listing.id,
                            embedding = embedding.toVectorString(),
                            embeddedAt = LocalDateTime.now(),
                            embeddingModel = "gemini-embedding-001",
                        )
                        processedCount++
                    }
                    log.info("[Activity Backfill] 배치 {}/{} 완료 ({}건)", index + 1, chunks.size, batch.size)
                    success = true
                } catch (e: Exception) {
                    retries++
                    if (retries > maxRetries) {
                        log.warn("[Activity Backfill] 배치 {}/{} 최종 실패 ({}회 재시도): {}", index + 1, chunks.size, maxRetries, e.message)
                        failedCount += batch.size
                    } else {
                        val backoff = retries * 30_000L
                        log.info("[Activity Backfill] 배치 {}/{} 429 발생, {}초 후 재시도 ({}/{})", index + 1, chunks.size, backoff / 1000, retries, maxRetries)
                        Thread.sleep(backoff)
                    }
                }
            }

            if (index < chunks.size - 1) {
                Thread.sleep(1000)
            }
        }

        val remainingUnembedded = activityListingRepository.findByEmbeddingIsNull().size
        return ActivityBackfillResponse(
            processedCount = processedCount,
            failedCount = failedCount,
            totalUnembedded = remainingUnembedded,
        )
    }

    private fun runSingle(client: ExternalActivityClient): ActivityIngestionResponse {
        val run = IngestionRun(
            sourceName = client.sourceName(),
            startedAt = LocalDateTime.now(),
        )
        ingestionRunRepository.save(run)

        return try {
            val activities = client.fetchActivities()
            var newCount = 0
            var duplicateCount = 0
            var failedCount = 0

            val existingSourceIds = activityListingRepository
                .findSourceIdsBySourceName(client.sourceName())
                .toSet()

            val deduplicatedActivities = activities.distinctBy { it.sourceId }
            val batchDuplicateCount = activities.size - deduplicatedActivities.size
            if (batchDuplicateCount > 0) {
                log.info("[Activity] 배치 내 중복 sourceId {}건 제거", batchDuplicateCount)
            }

            val incomingSourceIds = deduplicatedActivities.map { it.sourceId }.toSet()

            if (incomingSourceIds.isNotEmpty()) {
                activityListingRepository.deleteBySourceNameAndSourceIdNotIn(
                    client.sourceName(),
                    incomingSourceIds.toList(),
                )
            }

            val newListings = mutableListOf<ActivityListing>()
            duplicateCount += batchDuplicateCount
            for (dto in deduplicatedActivities) {
                if (dto.sourceId in existingSourceIds) {
                    duplicateCount++
                    continue
                }

                try {
                    val listing = activityListingSaver.save(
                        ActivityListing(
                            title = stripHtml(dto.title),
                            organizer = stripHtml(dto.organizer),
                            url = dto.url,
                            category = dto.category?.let { stripHtml(it) },
                            startDate = dto.startDate,
                            endDate = dto.endDate,
                            description = dto.description?.let { stripHtml(it) },
                            sourceName = client.sourceName(),
                            sourceId = dto.sourceId,
                        ),
                    )
                    newListings.add(listing)
                    newCount++
                } catch (e: Exception) {
                    log.warn("[Activity] 저장 실패 (sourceId={}): {}", dto.sourceId, e.message)
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

            run.toActivityResponse()
        } catch (e: Exception) {
            run.success = false
            run.completedAt = LocalDateTime.now()
            ingestionRunRepository.save(run)
            throw e
        }
    }

    private fun embedNewListings(listings: List<ActivityListing>) {
        if (listings.isEmpty()) return

        val embeddable = listings.filter { buildEmbeddingText(it).isNotBlank() }
        if (embeddable.isEmpty()) {
            log.warn("[Activity] 임베딩할 텍스트가 있는 항목이 없습니다 ({}건 스킵)", listings.size)
            return
        }

        embeddable.chunked(10).forEach { batch ->
            val texts = batch.map { buildEmbeddingText(it) }
            try {
                val embeddings = embeddingService.embedTexts(texts)
                batch.zip(embeddings).forEach { (listing, embedding) ->
                    activityListingRepository.updateEmbedding(
                        id = listing.id,
                        embedding = embedding.toVectorString(),
                        embeddedAt = LocalDateTime.now(),
                        embeddingModel = "gemini-embedding-001",
                    )
                }
            } catch (e: Exception) {
                log.warn("[Activity] 임베딩 생성 실패 ({}건), 나중에 backfill 가능: {}", batch.size, e.message)
            }
        }
    }

    private fun buildEmbeddingText(listing: ActivityListing): String =
        listOfNotNull(listing.title, listing.organizer, listing.category, listing.description)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

private fun stripHtml(text: String): String =
    Jsoup.clean(text, Safelist.none()).trim()

fun IngestionRun.toActivityResponse() = ActivityIngestionResponse(
    sourceName = sourceName,
    newCount = newCount,
    duplicateCount = duplicateCount,
    failedCount = failedCount,
    success = success,
)
