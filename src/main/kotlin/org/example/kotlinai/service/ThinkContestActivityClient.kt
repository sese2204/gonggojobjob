package org.example.kotlinai.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.example.kotlinai.dto.response.ExternalActivityDto
import org.example.kotlinai.global.exception.AiServiceException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class ThinkContestActivityClient(
    @Value("\${thinkcontest.api.url}") private val apiUrl: String,
    @Value("\${thinkcontest.api.records-per-page:10}") private val recordsPerPage: Int,
    @Value("\${thinkcontest.api.max-pages:20}") private val maxPages: Int,
    @Value("\${thinkcontest.api.page-delay-ms:300}") private val pageDelayMs: Long,
) : ExternalActivityClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()
    private val restClient by lazy { RestClient.builder().baseUrl(apiUrl).build() }
    private val activeStatuses = setOf("ING", "INGEND")

    override fun sourceName() = "thinkcontest"

    override fun fetchActivities(): List<ExternalActivityDto> {
        log.info("[씽굿] API 호출 시작")

        return try {
            val contests = fetchFromEndpoint("/contest/subList.do", "공모전")
            val outsides = fetchFromEndpoint("/outside/subList.do", "대외활동")

            val merged = (contests + outsides).distinctBy { it.sourceId }
            log.info("[씽굿] API 호출 완료 — 공모전: {}건, 대외활동: {}건, 통합(중복 제거): {}건",
                contests.size, outsides.size, merged.size)
            merged
        } catch (e: AiServiceException) {
            throw e
        } catch (e: Exception) {
            log.error("[씽굿] API 호출 실패 — {}", e.message, e)
            throw AiServiceException("씽굿 API 호출 실패: ${e.message}", e)
        }
    }

    private fun fetchFromEndpoint(endpoint: String, label: String): List<ExternalActivityDto> {
        val allActivities = mutableListOf<ExternalActivityDto>()
        val seenIds = mutableSetOf<String>()
        var page = 1

        do {
            val response = fetchPage(endpoint, page)

            val activities = response.listJsonData
                .filter { it.process in activeStatuses }
                .mapNotNull { mapToDto(it) }
                .filter { seenIds.add(it.sourceId) }
            allActivities.addAll(activities)

            log.debug("[씽굿] {} 페이지 {} — {}건 수집 (전체: {}건)", label, page, activities.size, response.totalcnt)

            page++
            if (page <= maxPages && (page - 1) * recordsPerPage < response.totalcnt && pageDelayMs > 0) {
                Thread.sleep(pageDelayMs)
            }
        } while ((page - 1) * recordsPerPage < response.totalcnt && page <= maxPages)

        return allActivities
    }

    private fun fetchPage(endpoint: String, page: Int): ThinkContestResponse {
        val requestBody = objectMapper.writeValueAsString(
            ThinkContestRequest(
                recordsPerPage = recordsPerPage,
                currentPageNo = page,
                searchStatus = "Y",
            ),
        )

        val responseBody = restClient.post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .retrieve()
            .onStatus({ it.isError }) { _, response ->
                log.error("[씽굿] API 오류 응답 — status={}", response.statusCode)
                throw AiServiceException("씽굿 API 오류: HTTP ${response.statusCode}")
            }
            .body(String::class.java)
            ?: throw AiServiceException("씽굿 API가 빈 응답을 반환했습니다.")

        return objectMapper.readValue(responseBody)
    }

    private fun mapToDto(item: ThinkContestItem): ExternalActivityDto? {
        if (item.contestPk.isBlank() || item.programNm.isBlank()) {
            log.warn("[씽굿] 필수 필드 누락으로 항목 건너뜀 — contestPk='{}', programNm='{}'", item.contestPk, item.programNm)
            return null
        }
        return ExternalActivityDto(
            sourceId = item.contestPk,
            title = item.programNm,
            organizer = item.hostCompany ?: "",
            url = "https://www.thinkcontest.com/thinkgood/user/${item.regType}/view.do?contest_pk=${item.contestPk}",
            category = item.contestFieldNm,
            startDate = item.acceptDt,
            endDate = item.finishDt,
            description = buildDescription(item),
        )
    }

    private fun buildDescription(item: ThinkContestItem): String =
        listOfNotNull(
            item.enterQualifiedNm?.takeIf { it.isNotBlank() },
            item.prizeMoney?.takeIf { it.isNotBlank() }?.let { "상금: $it" },
        ).joinToString(" / ")
}

internal data class ThinkContestRequest(
    val recordsPerPage: Int,
    val currentPageNo: Int,
    val searchStatus: String,
    val sidx: String = "",
    val sord: String = "",
    @JsonProperty("contest_field") val contestField: String = "",
    @JsonProperty("host_organ") val hostOrgan: String = "",
    @JsonProperty("enter_qualified") val enterQualified: String = "",
    @JsonProperty("award_size") val awardSize: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ThinkContestResponse(
    val status: Int = 0,
    val totalcnt: Int = 0,
    val listJsonData: List<ThinkContestItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ThinkContestItem(
    @JsonProperty("contest_pk") val contestPk: String = "",
    @JsonProperty("program_nm") val programNm: String = "",
    @JsonProperty("host_company") val hostCompany: String? = null,
    @JsonProperty("contest_field_nm") val contestFieldNm: String? = null,
    @JsonProperty("accept_dt") val acceptDt: String? = null,
    @JsonProperty("finish_dt") val finishDt: String? = null,
    val process: String = "",
    @JsonProperty("reg_type") val regType: String = "contest",
    @JsonProperty("enter_qualified_nm") val enterQualifiedNm: String? = null,
    @JsonProperty("prize_money") val prizeMoney: String? = null,
)
