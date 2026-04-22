package org.example.kotlinai.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.example.kotlinai.dto.request.ActivitySearchRequest
import org.example.kotlinai.entity.ActivityListing
import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.service.ActivitySearchService
import org.example.kotlinai.service.SearchCacheService
import org.example.kotlinai.service.UpstageGroundednessService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate

/**
 * One-shot helper. Builds a labeling candidate pool per query and asks Gemini
 * to score each candidate 0/1/2. Writes human-reviewable YAML so the developer
 * can copy confirmed IDs into eval-queries.yaml.
 *
 * Run: EVAL_LABEL=label ./gradlew test --tests LabelingHelperTest -PincludeTags=eval-label
 */
@SpringBootTest
@ActiveProfiles("eval")
@Tag("eval-label")
class LabelingHelperTest(
    @Autowired private val activitySearchService: ActivitySearchService,
    @Autowired private val activityListingRepository: ActivityListingRepository,
    @Autowired private val searchCacheService: SearchCacheService,
    @Autowired private val upstageGroundednessService: UpstageGroundednessService,
) {
    private val log = LoggerFactory.getLogger(LabelingHelperTest::class.java)
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @Value("\${gemini.api.key}")
    private lateinit var apiKey: String

    @Value("\${gemini.api.url}")
    private lateinit var apiUrl: String

    private val client: RestClient by lazy {
        RestClient.builder()
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(60))
                setReadTimeout(Duration.ofSeconds(60))
            })
            .build()
    }

    @Test
    fun `generate labeling candidates`() {
        val outputDir = System.getenv("EVAL_OUTPUT_DIR") ?: "src/test/resources/eval/labeling"
        Files.createDirectories(Paths.get(outputDir))

        val querySet = EvalIo.loadQuerySet("eval/eval-queries.yaml")
        SecurityContextHolder.clearContext()

        val out = StringBuilder()
        out.appendLine("# Labeling candidates — generated ${LocalDate.now()}")
        out.appendLine("# Review each candidate. Copy IDs with score >= 2 (or your judgement) into eval-queries.yaml#relevantIds")
        out.appendLine("queries:")

        querySet.queries.forEachIndexed { idx, q ->
            log.info("[{}/{}] labeling {} tags={} query='{}'", idx + 1, querySet.queries.size, q.id, q.tags, q.query)
            clearCache()

            val hybridPool = runCatching {
                activitySearchService.search(ActivitySearchRequest(tags = q.tags, query = q.query))
                    .activities.mapNotNull { it.id.toLongOrNull() }
            }.getOrDefault(emptyList())

            val ilikePool = ilikeCandidates(q.tags + q.query.split(" ").filter { it.isNotBlank() }, 15)
            val poolIds = (hybridPool + ilikePool).distinct().take(50)
            val listings = activityListingRepository.findAllById(poolIds).associateBy { it.id }

            val scored = if (poolIds.isEmpty()) emptyList()
            else runCatching { scoreWithGemini(q, poolIds.mapNotNull { listings[it] }) }
                .onFailure { log.warn("[labeling] {} gemini failed: {}", q.id, it.message) }
                .getOrDefault(emptyList())

            val groundednessScored = if (poolIds.isEmpty()) emptyMap()
            else poolIds.mapNotNull { listings[it] }.associate { l ->
                val context = buildContext(l)
                val answer = buildAnswer(q)
                val groundedness = runCatching { upstageGroundednessService.check(context, answer) }
                    .onFailure { log.warn("[labeling] {} groundedness failed id={}: {}", q.id, l.id, it.message) }
                    .getOrDefault(UpstageGroundednessService.Groundedness.NOT_SURE)
                l.id to upstageGroundednessService.toScore(groundedness)
            }

            out.appendLine("  - id: ${q.id}")
            out.appendLine("    category: ${q.category}")
            out.appendLine("    tags: ${q.tags}")
            out.appendLine("    query: \"${q.query}\"")
            out.appendLine("    candidates:")
            poolIds.forEach { id ->
                val l = listings[id] ?: return@forEach
                val geminiScore = scored.firstOrNull { it.id == id }?.score ?: 0
                val upstageScore = groundednessScored[id] ?: 0
                out.appendLine("      - id: $id   # gemini=$geminiScore upstage=$upstageScore")
                out.appendLine("        title: ${escape(l.title)}")
                out.appendLine("        category: ${escape(l.category ?: "")}")
                out.appendLine("        organizer: ${escape(l.organizer)}")
                val snippet = (l.description ?: "").take(140).replace("\n", " ")
                if (snippet.isNotBlank()) out.appendLine("        snippet: ${escape(snippet)}")
            }
        }

        val file = File(outputDir, "candidates-${LocalDate.now()}.yaml")
        file.writeText(out.toString())
        log.info("[Labeling] wrote → {}", file.absolutePath)
    }

    private fun buildContext(listing: ActivityListing): String =
        listOfNotNull(
            listing.title,
            listing.organizer,
            listing.category,
            (listing.description ?: "").take(300),
        ).filter { it.isNotBlank() }.joinToString("\n")

    private fun buildAnswer(q: EvalQuery): String {
        val queryParts = (q.tags + q.query).filter { it.isNotBlank() }
        return "이 활동은 ${queryParts.joinToString(", ")} 관련 활동입니다."
    }

    private fun escape(s: String): String = "\"" + s.replace("\"", "\\\"").replace("\n", " ").trim() + "\""

    private fun ilikeCandidates(terms: List<String>, perTerm: Int): List<Long> =
        terms.filter { it.isNotBlank() }.flatMap { term ->
            runCatching {
                activityListingRepository.findByKeywordLike("%${term.trim()}%", perTerm).map { it.id }
            }.getOrDefault(emptyList())
        }.distinct()

    private data class LabelScore(val id: Long, val score: Int)

    private fun scoreWithGemini(q: EvalQuery, listings: List<ActivityListing>): List<LabelScore> {
        if (listings.isEmpty()) return emptyList()
        val summaries = listings.map {
            mapOf(
                "id" to it.id.toString(),
                "title" to it.title,
                "category" to (it.category ?: ""),
                "organizer" to it.organizer,
                "description" to (it.description ?: "").take(200),
            )
        }

        val prompt = """
            당신은 검색 결과 관련성 평가자입니다. 아래 사용자 질의와 후보 활동 목록에 대해,
            각 후보가 질의와 얼마나 관련있는지 0~2 점수로 채점하세요.
            - 0 = 무관 (카테고리/주제가 완전히 다름)
            - 1 = 약관련 (일부 키워드만 스침, 부차적)
            - 2 = 매우관련 (질의 의도에 정확히 부합)

            사용자 조건:
            - 태그: ${mapper.writeValueAsString(q.tags)}
            - 검색어: "${q.query}"

            후보:
            ${mapper.writeValueAsString(summaries)}

            오로지 JSON 배열만 반환: [{"id":"후보id","score":0|1|2}]
        """.trimIndent()

        val body = mapOf(
            "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to prompt)))),
            "generationConfig" to mapOf("responseMimeType" to "application/json"),
        )

        val response = client.post()
            .uri(URI.create("$apiUrl?key=$apiKey"))
            .header("Content-Type", "application/json")
            .body(mapper.writeValueAsString(body))
            .retrieve()
            .body(Map::class.java) ?: return emptyList()

        val jsonText = extractText(response) ?: return emptyList()
        val parsed: List<Map<String, Any>> = mapper.readValue(jsonText)
        return parsed.mapNotNull {
            val id = (it["id"] as? String)?.toLongOrNull() ?: return@mapNotNull null
            val score = (it["score"] as? Number)?.toInt() ?: 0
            LabelScore(id, score)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractText(response: Map<*, *>): String? {
        val candidates = response["candidates"] as? List<*> ?: return null
        val first = candidates.firstOrNull() as? Map<*, *> ?: return null
        val content = first["content"] as? Map<*, *> ?: return null
        val parts = content["parts"] as? List<*> ?: return null
        val firstPart = parts.firstOrNull() as? Map<*, *> ?: return null
        return firstPart["text"] as? String
    }

    private fun clearCache() {
        val field = SearchCacheService::class.java.getDeclaredField("cache")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(searchCacheService) as MutableMap<String, *>
        map.clear()
    }
}
