package org.example.kotlinai.eval

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class EvalQuery(
    val id: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val query: String = "",
    val relevantIds: List<Long> = emptyList(),
    val marginalIds: List<Long> = emptyList(),
    val notes: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EvalQuerySet(
    val version: Int = 1,
    val snapshotDate: String? = null,
    val queries: List<EvalQuery> = emptyList(),
)

data class EvalQueryResult(
    val queryId: String,
    val category: String,
    val tags: List<String>,
    val query: String,
    val returnedIds: List<Long>,
    val relevantIds: List<Long>,
    val recallAt10: Double,
    val ndcgAt10: Double,
    val precisionAt5: Double,
    val mrr: Double,
    val isZeroResult: Boolean,
    val latencyMs: Long,
)

data class EvalSummary(
    val recallAt10Mean: Double,
    val ndcgAt10Mean: Double,
    val precisionAt5Mean: Double,
    val mrrMean: Double,
    val zeroResultRate: Double,
    val latencyP50Ms: Long,
    val latencyP95Ms: Long,
    val queryCount: Int,
    val labelCoverage: Double,
)

data class EvalReport(
    val label: String,
    val timestamp: String,
    val commit: String?,
    val snapshotDate: String?,
    val summary: EvalSummary,
    val byCategory: Map<String, EvalSummary>,
    val perQuery: List<EvalQueryResult>,
)

object EvalIo {

    private val yaml: ObjectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())
    private val json: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        .apply { writerWithDefaultPrettyPrinter() }

    fun loadQuerySet(resourcePath: String): EvalQuerySet {
        val stream = EvalIo::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: error("eval query resource not found: $resourcePath")
        return stream.use { yaml.readValue(it) }
    }

    fun writeReport(report: EvalReport, outputDir: String, label: String): File {
        val dir = Paths.get(outputDir)
        Files.createDirectories(dir)
        val fileName = "$label-${LocalDate.now()}.json"
        val file = dir.resolve(fileName).toFile()
        json.writerWithDefaultPrettyPrinter().writeValue(file, report)
        return file
    }

    fun summarize(results: List<EvalQueryResult>): EvalSummary {
        if (results.isEmpty()) {
            return EvalSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0.0)
        }
        val labeled = results.filter { it.relevantIds.isNotEmpty() }
        val latencies = results.map { it.latencyMs }.sorted()
        fun percentile(p: Double): Long {
            if (latencies.isEmpty()) return 0
            val idx = ((latencies.size - 1) * p).toInt()
            return latencies[idx]
        }
        return EvalSummary(
            recallAt10Mean = labeled.map { it.recallAt10 }.averageOrZero(),
            ndcgAt10Mean = labeled.map { it.ndcgAt10 }.averageOrZero(),
            precisionAt5Mean = labeled.map { it.precisionAt5 }.averageOrZero(),
            mrrMean = labeled.map { it.mrr }.averageOrZero(),
            zeroResultRate = results.count { it.isZeroResult }.toDouble() / results.size,
            latencyP50Ms = percentile(0.5),
            latencyP95Ms = percentile(0.95),
            queryCount = results.size,
            labelCoverage = labeled.size.toDouble() / results.size,
        )
    }

    private fun List<Double>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else filter { !it.isNaN() }.let { if (it.isEmpty()) 0.0 else it.average() }
}
