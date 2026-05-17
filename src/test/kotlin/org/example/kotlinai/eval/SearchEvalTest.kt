package org.example.kotlinai.eval

import org.example.kotlinai.dto.request.ActivitySearchRequest
import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.service.ActivitySearchService
import org.example.kotlinai.service.SearchCacheService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

@SpringBootTest
@ActiveProfiles("local", "eval")
@Tag("eval")
class SearchEvalTest(
    @Autowired private val activitySearchService: ActivitySearchService,
    @Autowired private val activityListingRepository: ActivityListingRepository,
    @Autowired private val searchCacheService: SearchCacheService,
) {
    private val log = LoggerFactory.getLogger(SearchEvalTest::class.java)

    @Test
    fun `run eval and dump report`() {
        val label = System.getenv("EVAL_LABEL") ?: "unknown"
        val outputDir = System.getenv("EVAL_OUTPUT_DIR") ?: "src/test/resources/eval/results"

        val querySet = EvalIo.loadQuerySet("eval/eval-queries.yaml")
        val dbRowCount = activityListingRepository.count()
        log.info("[Eval] label={}, db rows={}, queries={}", label, dbRowCount, querySet.queries.size)

        SecurityContextHolder.clearContext()

        val perQuery = querySet.queries.mapIndexed { index, q ->
            clearCache()
            val request = ActivitySearchRequest(tags = q.tags, query = q.query)

            lateinit var response: org.example.kotlinai.dto.response.ActivitySearchResponse
            val coldLatency = measureTimeMillis { response = activitySearchService.search(request) }

            val returnedIds = response.activities.mapNotNull { it.id.toLongOrNull() }
            val relevantSet = q.relevantIds.toSet()

            val result = EvalQueryResult(
                queryId = q.id,
                category = q.category,
                tags = q.tags,
                query = q.query,
                returnedIds = returnedIds,
                relevantIds = q.relevantIds,
                recallAt10 = EvalMetrics.recallAtK(returnedIds, relevantSet, 10),
                ndcgAt10 = EvalMetrics.ndcgAtK(returnedIds, relevantSet, 10),
                precisionAt5 = EvalMetrics.precisionAtK(returnedIds, relevantSet, 5),
                mrr = EvalMetrics.mrr(returnedIds, relevantSet),
                isZeroResult = returnedIds.isEmpty(),
                latencyMs = coldLatency,
            )
            log.info(
                "[Eval] ({}/{}) {} cat={} tags={} query='{}' returned={} recall@10={} latency={}ms",
                index + 1, querySet.queries.size, q.id, q.category, q.tags, q.query,
                returnedIds.size, "%.3f".format(result.recallAt10), coldLatency,
            )
            result
        }

        val summary = EvalIo.summarize(perQuery)
        val byCategory = perQuery.groupBy { it.category }
            .mapValues { (_, v) -> EvalIo.summarize(v) }

        val report = EvalReport(
            label = label,
            timestamp = LocalDateTime.now().toString(),
            commit = readGitCommit(),
            snapshotDate = querySet.snapshotDate,
            summary = summary,
            byCategory = byCategory,
            perQuery = perQuery,
        )

        val outFile = EvalIo.writeReport(report, outputDir, label)
        log.info("[Eval] wrote report → {}", outFile.absolutePath)
        printSummary(report)
    }

    private fun clearCache() {
        val field = SearchCacheService::class.java.getDeclaredField("cache")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(searchCacheService) as MutableMap<String, *>
        map.clear()
    }

    private fun readGitCommit(): String? = try {
        val p = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .redirectErrorStream(true).start()
        p.inputStream.bufferedReader().use { it.readText().trim() }.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun printSummary(report: EvalReport) {
        val s = report.summary
        log.info("=".repeat(72))
        log.info("[Eval] label={} commit={} queries={}", report.label, report.commit, s.queryCount)
        log.info(
            "[Eval] Recall@10={} nDCG@10={} P@5={} MRR={} ZeroRate={} p50={}ms p95={}ms labelCov={}",
            "%.3f".format(s.recallAt10Mean),
            "%.3f".format(s.ndcgAt10Mean),
            "%.3f".format(s.precisionAt5Mean),
            "%.3f".format(s.mrrMean),
            "%.3f".format(s.zeroResultRate),
            s.latencyP50Ms, s.latencyP95Ms,
            "%.2f".format(s.labelCoverage),
        )
        report.byCategory.forEach { (cat, cs) ->
            log.info(
                "[Eval] {} → Recall@10={} nDCG@10={} ZeroRate={} n={}",
                cat,
                "%.3f".format(cs.recallAt10Mean),
                "%.3f".format(cs.ndcgAt10Mean),
                "%.3f".format(cs.zeroResultRate),
                cs.queryCount,
            )
        }
        log.info("=".repeat(72))
    }
}
