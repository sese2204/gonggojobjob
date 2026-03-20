package org.example.kotlinai.service

import org.example.kotlinai.config.RagProperties
import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.repository.JobListingRepository
import org.example.kotlinai.service.EmbeddingService.Companion.toVectorString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class HybridSearchService(
    private val jobListingRepository: JobListingRepository,
    private val embeddingService: EmbeddingService,
    private val ragProperties: RagProperties,
) {
    private val log = LoggerFactory.getLogger(HybridSearchService::class.java)

    fun search(tags: List<String>, query: String, topN: Int = ragProperties.topN): List<JobListing> {
        val searchText = buildSearchText(tags, query)
        val candidatePool = topN * 2
        log.info("[Hybrid] searchText='{}', topN={}, candidatePool={}", searchText, topN, candidatePool)

        val vectorResults = fetchVectorResults(searchText, candidatePool)
        val keywordResults = fetchKeywordResults(searchText, candidatePool)

        log.info("[Hybrid] 벡터={}건, 키워드={}건", vectorResults.size, keywordResults.size)

        if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
            log.warn("[Hybrid] 벡터+키워드 모두 0건 → 빈 결과 반환")
            return emptyList()
        }

        val merged = mergeByRrf(vectorResults, keywordResults, topN)
        log.info("[Hybrid] RRF 병합 후 최종 {}건", merged.size)
        return merged
    }

    private fun fetchVectorResults(searchText: String, limit: Int): List<JobListing> =
        try {
            val queryVector = embeddingService.embedText(searchText, "RETRIEVAL_QUERY")
            log.debug("[Hybrid-Vector] 임베딩 생성 완료 (dim={})", queryVector.size)
            val results = jobListingRepository.findByVectorSimilarity(queryVector.toVectorString(), limit)
            log.info("[Hybrid-Vector] {}건 조회", results.size)
            results
        } catch (e: Exception) {
            log.warn("[Hybrid-Vector] 벡터 검색 실패, 키워드로 폴백: {}", e.message)
            emptyList()
        }

    private fun fetchKeywordResults(searchText: String, limit: Int): List<JobListing> {
        val tsQuery = searchText.trim().split("\\s+".toRegex()).joinToString(" & ")
        log.info("[Hybrid-Keyword] tsQuery='{}', limit={}", tsQuery, limit)

        val results = jobListingRepository.findByKeyword(tsQuery, limit)
        log.info("[Hybrid-Keyword] full-text 결과: {}건", results.size)
        if (results.isNotEmpty()) return results

        // Trigram fallback
        val pattern = "%${searchText.trim()}%"
        log.info("[Hybrid-Keyword] full-text 0건 → trigram 폴백, pattern='{}'", pattern)
        val trigramResults = jobListingRepository.findByKeywordLike(pattern, limit)
        log.info("[Hybrid-Keyword] trigram 결과: {}건", trigramResults.size)
        return trigramResults
    }

    private fun mergeByRrf(
        vectorResults: List<JobListing>,
        keywordResults: List<JobListing>,
        topN: Int,
    ): List<JobListing> {
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
