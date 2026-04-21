package org.example.kotlinai.service

import org.example.kotlinai.config.RagProperties
import org.example.kotlinai.entity.ActivityListing
import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.service.EmbeddingService.Companion.toVectorString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ActivityHybridSearchService(
    private val activityListingRepository: ActivityListingRepository,
    private val embeddingService: EmbeddingService,
    private val ragProperties: RagProperties,
) {
    private val log = LoggerFactory.getLogger(ActivityHybridSearchService::class.java)

    fun search(tags: List<String>, query: String, topN: Int = ragProperties.topN): List<ActivityListing> {
        val searchText = buildSearchText(tags, query)
        val candidatePool = topN * 2
        log.info("[ActivityHybrid] searchText='{}', topN={}, candidatePool={}", searchText, topN, candidatePool)

        val vectorResults = fetchVectorResults(searchText, candidatePool)
        val keywordResults = fetchKeywordResults(searchText, candidatePool)

        log.info("[ActivityHybrid] vector={}items, keyword={}items", vectorResults.size, keywordResults.size)

        if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
            log.warn("[ActivityHybrid] vector+keyword both 0 results")
            return emptyList()
        }

        val merged = mergeByRrf(vectorResults, keywordResults, topN)
        log.info("[ActivityHybrid] RRF merged: {} results", merged.size)
        return merged
    }

    private fun fetchVectorResults(searchText: String, limit: Int): List<ActivityListing> =
        try {
            val queryVector = embeddingService.embedText(searchText, "RETRIEVAL_QUERY")
            activityListingRepository.findByVectorSimilarity(queryVector.toVectorString(), limit)
        } catch (e: Exception) {
            log.warn("[ActivityHybrid-Vector] vector search failed, fallback to keyword: {}", e.message)
            emptyList()
        }

    private fun fetchKeywordResults(searchText: String, limit: Int): List<ActivityListing> {
        val tokens = searchText.trim()
            .replace("[()/<>&|!:*\\\\]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return emptyList()

        // OR + prefix matching: "인턴:* | IT:*"
        //   - OR so 2~3 토큰 쿼리가 모든 토큰을 요구하지 않도록
        //   - prefix(:*)로 "인턴" 입력이 "인턴십"/"인턴쉽" 매칭
        val tsQuery = tokens.joinToString(" | ") { "$it:*" }
        return activityListingRepository.findByKeyword(tsQuery, limit)
    }

    private fun mergeByRrf(
        vectorResults: List<ActivityListing>,
        keywordResults: List<ActivityListing>,
        topN: Int,
    ): List<ActivityListing> {
        val k = 60.0
        val vectorWeight = ragProperties.vectorWeight
        val keywordWeight = ragProperties.keywordWeight

        val vectorRanks = vectorResults.mapIndexed { index, listing -> listing.id to (index + 1) }.toMap()
        val keywordRanks = keywordResults.mapIndexed { index, listing -> listing.id to (index + 1) }.toMap()

        val allListings = (vectorResults + keywordResults).associateBy { it.id }

        val scores = allListings.keys.map { id ->
            val vecScore = vectorRanks[id]?.let { vectorWeight * (1.0 / (k + it)) } ?: 0.0
            val kwScore = keywordRanks[id]?.let { keywordWeight * (1.0 / (k + it)) } ?: 0.0
            id to (vecScore + kwScore)
        }

        return scores
            .sortedByDescending { it.second }
            .take(topN)
            .mapNotNull { allListings[it.first] }
    }

    private fun buildSearchText(tags: List<String>, query: String): String =
        (tags + query).filter { it.isNotBlank() }.joinToString(" ")
}
