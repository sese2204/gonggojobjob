package org.example.kotlinai.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.example.kotlinai.dto.response.ExternalActivityDto
import org.example.kotlinai.global.exception.AiServiceException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class AllConActivityClient(
    @Value("\${allcon.api.url}") private val apiUrl: String,
    @Value("\${allcon.api.max-pages:20}") private val maxPages: Int,
    @Value("\${allcon.api.page-delay-ms:300}") private val pageDelayMs: Long,
    @Value("\${allcon.api.page-size:15}") private val pageSize: Int,
) : ExternalActivityClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()
    private val restClient by lazy { RestClient.builder().baseUrl(apiUrl).build() }

    override fun sourceName() = "allcon"

    override fun fetchActivities(): List<ExternalActivityDto> {
        log.info("[올콘] API 호출 시작 — 최대 {}페이지", maxPages)

        return try {
            val allActivities = mutableListOf<ExternalActivityDto>()
            val seenIds = mutableSetOf<String>()
            var page = 1

            do {
                val responseBody = fetchPage(page)
                val response = objectMapper.readValue<AllConResponse>(responseBody)

                val activities = response.rows
                    .filter { it.cl_status.contains("접수중") || it.cl_status.contains("마감임박") }
                    .mapNotNull { mapToDto(it) }
                    .filter { seenIds.add(it.sourceId) }
                allActivities.addAll(activities)

                log.debug("[올콘] 페이지 {}/{} — {}건 수집", page, response.totalPage, activities.size)

                page++
                if (page <= response.totalPage && page <= maxPages && pageDelayMs > 0) {
                    Thread.sleep(pageDelayMs)
                }
            } while (page <= response.totalPage && page <= maxPages)

            log.info("[올콘] API 호출 완료 — 전체: {}건", allActivities.size)
            allActivities
        } catch (e: AiServiceException) {
            throw e
        } catch (e: Exception) {
            log.error("[올콘] API 호출 실패 — {}", e.message, e)
            throw AiServiceException("올콘 API 호출 실패: ${e.message}", e)
        }
    }

    private fun fetchPage(page: Int): String =
        restClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .queryParam("page", page)
                    .queryParam("dataRows", pageSize)
                    .build()
            }
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .header("Accept", "application/json")
            .retrieve()
            .onStatus({ it.isError }) { _, response ->
                log.error("[올콘] API 오류 응답 — status={}", response.statusCode)
                throw AiServiceException("올콘 API 오류: HTTP ${response.statusCode}")
            }
            .body(String::class.java)
            ?: throw AiServiceException("올콘 API가 빈 응답을 반환했습니다.")

    private fun mapToDto(item: AllConItem): ExternalActivityDto? {
        if (item.cl_srl.isBlank() || item.cl_title.isBlank()) {
            log.warn("[올콘] 필수 필드 누락으로 항목 건너뜀 — cl_srl='{}', cl_title='{}'", item.cl_srl, item.cl_title)
            return null
        }
        return ExternalActivityDto(
            sourceId = item.cl_srl,
            title = item.cl_title,
            organizer = item.cl_host,
            url = "https://www.all-con.co.kr/view/contest/${item.cl_srl}",
            category = item.cl_cate,
            startDate = item.cl_start_date,
            endDate = item.cl_end_date,
            description = listOfNotNull(
                item.cl_target?.takeIf { it.isNotBlank() },
                item.cl_status.takeIf { it.isNotBlank() },
            ).joinToString(" / "),
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class AllConResponse(
    val totalPage: Int = 0,
    val totalCount: Int = 0,
    val rows: List<AllConItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class AllConItem(
    val cl_srl: String = "",
    val cl_title: String = "",
    val cl_host: String = "",
    val cl_cate: String? = null,
    val cl_target: String? = null,
    val cl_start_date: String? = null,
    val cl_end_date: String? = null,
    val cl_status: String = "",
)
