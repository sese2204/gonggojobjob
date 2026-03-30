package org.example.kotlinai.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.example.kotlinai.dto.response.ExternalJobDto
import org.example.kotlinai.global.exception.AiServiceException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class WantedJobClient(
    @Value("\${wanted.api.url}") private val apiUrl: String,
    @Value("\${wanted.api.timeout-seconds:30}") private val timeoutSeconds: Long,
    @Value("\${wanted.api.country:kr}") private val country: String,
    @Value("\${wanted.api.job-sort:job.latest_order}") private val jobSort: String,
    @Value("\${wanted.api.years:0,1}") private val yearsStr: String,
    @Value("\${wanted.api.locations:all}") private val locations: String,
    @Value("\${wanted.api.limit:20}") private val limit: Int,
    @Value("\${wanted.api.max-pages-per-category:5}") private val maxPagesPerCategory: Int,
    @Value("\${wanted.api.page-delay-ms:500}") private val pageDelayMs: Long,
    @Value("\${wanted.api.tag-type-ids:518,523,511,507,530,508,517,513,510}") private val tagTypeIdsStr: String,
) : ExternalJobClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()
    private val restClient by lazy { RestClient.builder().baseUrl(apiUrl).build() }

    override fun sourceName() = "wanted"

    override fun fetchJobs(): List<ExternalJobDto> {
        val tagTypeIds = tagTypeIdsStr.split(",").map { it.trim() }
        log.info("[원티드] API 호출 시작 — {}개 카테고리, 카테고리당 최대 {}페이지", tagTypeIds.size, maxPagesPerCategory)

        return try {
            val allJobs = mutableListOf<ExternalJobDto>()
            val seenIds = mutableSetOf<String>()

            for (tagTypeId in tagTypeIds) {
                val categoryJobs = fetchCategory(tagTypeId, seenIds)
                allJobs.addAll(categoryJobs)
                log.info("[원티드] 카테고리 {} 완료 — {}건 수집", tagTypeId, categoryJobs.size)
            }

            log.info("[원티드] API 호출 완료 — 전체: {}건 ({}개 카테고리)", allJobs.size, tagTypeIds.size)
            allJobs
        } catch (e: AiServiceException) {
            throw e
        } catch (e: Exception) {
            log.error("[원티드] API 호출 실패 — {}", e.message, e)
            throw AiServiceException("원티드 API 호출 실패: ${e.message}", e)
        }
    }

    private fun fetchCategory(tagTypeId: String, seenIds: MutableSet<String>): List<ExternalJobDto> {
        val categoryJobs = mutableListOf<ExternalJobDto>()
        var offset = 0
        var page = 0

        do {
            val responseBody = fetchPage(offset, tagTypeId)
            val response = objectMapper.readValue<WantedResponse>(responseBody)

            val jobs = response.data.mapNotNull { job -> mapToDto(job) }
                .filter { seenIds.add(it.sourceId) }
            categoryJobs.addAll(jobs)

            log.debug("[원티드] 카테고리 {} 페이지 {} — {}건 (offset={})", tagTypeId, page + 1, jobs.size, offset)

            page++
            offset += limit

            if (response.links.next != null && page < maxPagesPerCategory && pageDelayMs > 0) {
                Thread.sleep(pageDelayMs)
            }
        } while (response.links.next != null && page < maxPagesPerCategory)

        return categoryJobs
    }

    private fun fetchPage(offset: Int, tagTypeId: String): String {
        val years = yearsStr.split(",").map { it.trim() }

        return restClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .queryParam("country", country)
                    .queryParam("job_sort", jobSort)
                    .queryParam("locations", locations)
                    .queryParam("limit", limit)
                    .queryParam("offset", offset)
                    .queryParam("tag_type_ids", tagTypeId)
                years.forEach { year -> uriBuilder.queryParam("years", year) }
                uriBuilder.build()
            }
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .header("Referer", "https://www.wanted.co.kr/")
            .header("Accept", "application/json, text/plain, */*")
            .retrieve()
            .onStatus({ it.isError }) { _, response ->
                log.error("[원티드] API 오류 응답 — status={}", response.statusCode)
                throw AiServiceException("원티드 API 오류: HTTP ${response.statusCode}")
            }
            .body(String::class.java)
            ?: throw AiServiceException("원티드 API가 빈 응답을 반환했습니다.")
    }

    private fun mapToDto(job: WantedJob): ExternalJobDto? {
        if (job.id == 0 || job.position.isBlank()) {
            log.warn("[원티드] 필수 필드 누락으로 공고 건너뜀 — id={}, position='{}'", job.id, job.position)
            return null
        }
        return ExternalJobDto(
            sourceId = job.id.toString(),
            title = job.position,
            company = job.company.name,
            url = "https://www.wanted.co.kr/wd/${job.id}",
            description = buildDescription(job),
        )
    }

    private fun buildDescription(job: WantedJob): String {
        val parts = listOfNotNull(
            job.company.industryName?.takeIf { it.isNotBlank() },
            job.address?.fullLocation?.takeIf { it.isNotBlank() },
        )
        return parts.joinToString(" / ")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class WantedResponse(
    val data: List<WantedJob> = emptyList(),
    val links: WantedLinks = WantedLinks(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class WantedLinks(
    val next: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class WantedJob(
    val id: Int = 0,
    val position: String = "",
    val company: WantedCompany = WantedCompany(),
    val address: WantedAddress? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class WantedCompany(
    val name: String = "",
    @JsonProperty("industry_name")
    val industryName: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class WantedAddress(
    @JsonProperty("full_location")
    val fullLocation: String? = null,
)
