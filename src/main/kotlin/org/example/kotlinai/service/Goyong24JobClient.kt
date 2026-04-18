package org.example.kotlinai.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import org.example.kotlinai.dto.response.ExternalJobDto
import org.example.kotlinai.global.exception.AiServiceException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration

@Service
class Goyong24JobClient(
    @Value("\${goyong24.api.key:}") private val apiKey: String,
    @Value("\${goyong24.api.url}") private val apiUrl: String,
    @Value("\${goyong24.api.timeout-seconds:30}") private val timeoutSeconds: Long,
) : ExternalJobClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val xmlMapper = XmlMapper().apply { registerKotlinModule() }
    private val restClient by lazy { RestClient.builder().baseUrl(apiUrl).build() }
    private val pageSize = 100

    override fun sourceName() = "goyong24"
    override fun supportsFullSync() = true

    override fun fetchJobs(): List<ExternalJobDto> {
        require(apiKey.isNotBlank()) { "고용24 API 키가 설정되지 않았습니다. GOYONG24_API_KEY 환경 변수를 확인하세요." }

        log.info("[고용24] API 호출 시작 — url={}", apiUrl)

        return try {
            val allJobs = mutableListOf<ExternalJobDto>()
            var page = 1
            var totalPages = 1

            do {
                val responseBody = fetchPage(page)
                val response = xmlMapper.readValue(responseBody, Goyong24Response::class.java)

                if (page == 1) {
                    totalPages = if (response.total > 0) (response.total + pageSize - 1) / pageSize else 1
                    log.info("[고용24] 전체 공고 수: {}건, 총 {}페이지", response.total, totalPages)
                }

                val jobs = response.jobs.mapNotNull { job ->
                    if (job.empSeqno.isBlank() || job.empWantedTitle.isBlank()) {
                        log.warn("[고용24] 필수 필드 누락으로 공고 건너뜀 — empSeqno='{}', title='{}'", job.empSeqno, job.empWantedTitle)
                        return@mapNotNull null
                    }
                    ExternalJobDto(
                        sourceId = job.empSeqno,
                        title = job.empWantedTitle,
                        company = job.empBusiNm,
                        url = job.empWantedHomepgDetail,
                        description = buildDescription(job),
                        deadline = job.empWantedEndt,
                    )
                }
                allJobs.addAll(jobs)
                log.debug("[고용24] 페이지 {}/{} 완료 — {}건 파싱", page, totalPages, jobs.size)
                page++
            } while (page <= totalPages)

            log.info("[고용24] API 호출 완료 — 전체 파싱 성공: {}건", allJobs.size)
            allJobs
        } catch (e: AiServiceException) {
            throw e
        } catch (e: Exception) {
            log.error("[고용24] API 호출 실패 — {}", e.message, e)
            throw AiServiceException("고용24 API 호출 실패: ${e.message}", e)
        }
    }

    private fun fetchPage(page: Int): String {
        return restClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .queryParam("authKey", apiKey)
                    .queryParam("callTp", "L")
                    .queryParam("returnType", "XML")
                    .queryParam("startPage", page)
                    .queryParam("display", pageSize)
                    .build()
            }
            .retrieve()
            .onStatus({ it.isError }) { _, response ->
                log.error("[고용24] API 오류 응답 — status={}", response.statusCode)
                throw AiServiceException("고용24 API 오류: HTTP ${response.statusCode}")
            }
            .body(String::class.java)
            ?: throw AiServiceException("고용24 API가 빈 응답을 반환했습니다.")
    }

    private fun buildDescription(job: Goyong24Job): String {
        val parts = listOfNotNull(
            job.empWantedTypeNm?.takeIf { it.isNotBlank() },
            job.coClcdNm?.takeIf { it.isNotBlank() },
            if (!job.empWantedStdt.isNullOrBlank() && !job.empWantedEndt.isNullOrBlank())
                "${job.empWantedStdt}~${job.empWantedEndt}" else null,
        )
        return parts.joinToString(" / ")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "dhsOpenEmpInfoList")
private data class Goyong24Response(
    val total: Int = 0,
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "dhsOpenEmpInfo")
    val jobs: List<Goyong24Job> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Goyong24Job(
    val empSeqno: String = "",
    val empWantedTitle: String = "",
    val empBusiNm: String = "",
    val coClcdNm: String? = null,
    val empWantedTypeNm: String? = null,
    val empWantedStdt: String? = null,
    val empWantedEndt: String? = null,
    val empWantedHomepgDetail: String = "",
)
