package org.example.kotlinai.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.example.kotlinai.global.exception.AiServiceException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WantedJobClientTest {

    private val objectMapper = jacksonObjectMapper()
    private val baseUrl = "https://www.wanted.co.kr/api/v4/jobs"

    private fun createClientWithServer(
        maxPagesPerCategory: Int = 5,
        pageDelayMs: Long = 0,
        tagTypeIds: String = "518",
    ): Pair<WantedJobClient, MockRestServiceServer> {
        val restClientBuilder = RestClient.builder().baseUrl(baseUrl)
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()

        val client = WantedJobClient(
            apiUrl = baseUrl,
            timeoutSeconds = 30,
            country = "kr",
            jobSort = "job.latest_order",
            yearsStr = "0,1",
            locations = "all",
            limit = 20,
            maxPagesPerCategory = maxPagesPerCategory,
            pageDelayMs = pageDelayMs,
            tagTypeIdsStr = tagTypeIds,
        )

        val restClientField = WantedJobClient::class.java.getDeclaredField("restClient\$delegate")
        restClientField.isAccessible = true

        val restClient = restClientBuilder.build()
        val newLazy = lazyOf(restClient)
        restClientField.set(client, newLazy)

        return client to server
    }

    private fun buildResponse(
        jobs: List<WantedJob>,
        nextUrl: String? = null,
    ): String = objectMapper.writeValueAsString(
        WantedResponse(
            data = jobs,
            links = WantedLinks(next = nextUrl),
        )
    )

    private fun sampleJob(id: Int = 12345, position: String = "백엔드 개발자") = WantedJob(
        id = id,
        position = position,
        company = WantedCompany(name = "테스트회사", industryName = "IT/웹"),
        address = WantedAddress(fullLocation = "서울 강남구"),
    )

    @Test
    fun `sourceName returns wanted`() {
        val (client, _) = createClientWithServer()
        assertEquals("wanted", client.sourceName())
    }

    @Test
    fun `fetchJobs correctly maps response to ExternalJobDto`() {
        val (client, server) = createClientWithServer()
        val job = sampleJob()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v4/jobs")))
            .andRespond(withSuccess(buildResponse(listOf(job)), MediaType.APPLICATION_JSON))

        val results = client.fetchJobs()

        assertEquals(1, results.size)
        val dto = results[0]
        assertEquals("12345", dto.sourceId)
        assertEquals("백엔드 개발자", dto.title)
        assertEquals("테스트회사", dto.company)
        assertEquals("https://www.wanted.co.kr/wd/12345", dto.url)
        assertEquals("IT/웹 / 서울 강남구", dto.description)

        server.verify()
    }

    @Test
    fun `fetchJobs handles pagination within a category`() {
        val (client, server) = createClientWithServer()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("offset=0")))
            .andRespond(
                withSuccess(
                    buildResponse(listOf(sampleJob(1, "첫번째")), nextUrl = "/api/v4/jobs?offset=20"),
                    MediaType.APPLICATION_JSON,
                )
            )
        server.expect(requestTo(org.hamcrest.Matchers.containsString("offset=20")))
            .andRespond(
                withSuccess(
                    buildResponse(listOf(sampleJob(2, "두번째")), nextUrl = null),
                    MediaType.APPLICATION_JSON,
                )
            )

        val results = client.fetchJobs()

        assertEquals(2, results.size)
        assertEquals("첫번째", results[0].title)
        assertEquals("두번째", results[1].title)

        server.verify()
    }

    @Test
    fun `fetchJobs stops at maxPagesPerCategory`() {
        val (client, server) = createClientWithServer(maxPagesPerCategory = 1)

        server.expect(requestTo(org.hamcrest.Matchers.containsString("offset=0")))
            .andRespond(
                withSuccess(
                    buildResponse(listOf(sampleJob(1, "첫번째")), nextUrl = "/api/v4/jobs?offset=20"),
                    MediaType.APPLICATION_JSON,
                )
            )

        val results = client.fetchJobs()

        assertEquals(1, results.size)
        server.verify()
    }

    @Test
    fun `fetchJobs collects from multiple categories and deduplicates`() {
        val (client, server) = createClientWithServer(tagTypeIds = "518,523")

        // Category 518
        server.expect(requestTo(org.hamcrest.Matchers.containsString("tag_type_ids=518")))
            .andRespond(
                withSuccess(
                    buildResponse(listOf(sampleJob(1, "개발자"), sampleJob(2, "중복공고"))),
                    MediaType.APPLICATION_JSON,
                )
            )
        // Category 523 — job id=2 is duplicate
        server.expect(requestTo(org.hamcrest.Matchers.containsString("tag_type_ids=523")))
            .andRespond(
                withSuccess(
                    buildResponse(listOf(sampleJob(2, "중복공고"), sampleJob(3, "마케터"))),
                    MediaType.APPLICATION_JSON,
                )
            )

        val results = client.fetchJobs()

        assertEquals(3, results.size)
        assertEquals(setOf("1", "2", "3"), results.map { it.sourceId }.toSet())

        server.verify()
    }

    @Test
    fun `fetchJobs skips jobs with missing required fields`() {
        val (client, server) = createClientWithServer()
        val validJob = sampleJob(1, "유효한 공고")
        val noIdJob = sampleJob(0, "ID없음")
        val blankPositionJob = WantedJob(id = 2, position = "", company = WantedCompany(name = "회사"))

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v4/jobs")))
            .andRespond(
                withSuccess(
                    buildResponse(listOf(validJob, noIdJob, blankPositionJob)),
                    MediaType.APPLICATION_JSON,
                )
            )

        val results = client.fetchJobs()

        assertEquals(1, results.size)
        assertEquals("유효한 공고", results[0].title)

        server.verify()
    }

    @Test
    fun `fetchJobs throws AiServiceException on HTTP error`() {
        val (client, server) = createClientWithServer()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v4/jobs")))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertFailsWith<AiServiceException> {
            client.fetchJobs()
        }

        server.verify()
    }

    @Test
    fun `fetchJobs builds description from industry and location`() {
        val (client, server) = createClientWithServer()

        val jobWithAllFields = sampleJob()
        val jobWithIndustryOnly = WantedJob(
            id = 2,
            position = "프론트엔드",
            company = WantedCompany(name = "회사A", industryName = "핀테크"),
            address = null,
        )
        val jobWithLocationOnly = WantedJob(
            id = 3,
            position = "디자이너",
            company = WantedCompany(name = "회사B", industryName = null),
            address = WantedAddress(fullLocation = "부산 해운대"),
        )

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v4/jobs")))
            .andRespond(
                withSuccess(
                    buildResponse(listOf(jobWithAllFields, jobWithIndustryOnly, jobWithLocationOnly)),
                    MediaType.APPLICATION_JSON,
                )
            )

        val results = client.fetchJobs()

        assertEquals("IT/웹 / 서울 강남구", results[0].description)
        assertEquals("핀테크", results[1].description)
        assertEquals("부산 해운대", results[2].description)

        server.verify()
    }
}
