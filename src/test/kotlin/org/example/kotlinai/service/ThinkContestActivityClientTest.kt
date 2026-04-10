package org.example.kotlinai.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.example.kotlinai.global.exception.AiServiceException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ThinkContestActivityClientTest {

    private val objectMapper = jacksonObjectMapper()
    private val baseUrl = "https://www.thinkcontest.com/thinkgood/user"

    private fun createClientWithServer(
        maxPages: Int = 20,
        pageDelayMs: Long = 0,
    ): Pair<ThinkContestActivityClient, MockRestServiceServer> {
        val restClientBuilder = RestClient.builder().baseUrl(baseUrl)
        val server = MockRestServiceServer.bindTo(restClientBuilder).ignoreExpectOrder(true).build()

        val client = ThinkContestActivityClient(
            apiUrl = baseUrl,
            recordsPerPage = 10,
            maxPages = maxPages,
            pageDelayMs = pageDelayMs,
        )

        val restClientField = ThinkContestActivityClient::class.java.getDeclaredField("restClient\$delegate")
        restClientField.isAccessible = true
        restClientField.set(client, lazyOf(restClientBuilder.build()))

        return client to server
    }

    private fun buildResponse(
        items: List<ThinkContestItem>,
        totalcnt: Int? = null,
    ): String = objectMapper.writeValueAsString(
        ThinkContestResponse(
            status = 1,
            totalcnt = totalcnt ?: items.size,
            listJsonData = items,
        ),
    )

    private fun sampleItem(
        pk: String = "100",
        name: String = "테스트 공모전",
        process: String = "ING",
        regType: String = "contest",
    ) = ThinkContestItem(
        contestPk = pk,
        programNm = name,
        hostCompany = "테스트 주최",
        contestFieldNm = "IT/디자인",
        acceptDt = "2026-04-01",
        finishDt = "2026-04-30",
        process = process,
        regType = regType,
        enterQualifiedNm = "대학생, 일반인",
        prizeMoney = "500만원",
    )

    @Test
    fun `sourceName returns thinkcontest`() {
        val (client, _) = createClientWithServer()
        assertEquals("thinkcontest", client.sourceName())
    }

    @Test
    fun `fetchActivities merges contests and outside activities`() {
        val (client, server) = createClientWithServer()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/contest/subList.do")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem("1", "공모전A"))), MediaType.APPLICATION_JSON))
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/outside/subList.do")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem("2", "대외활동B", regType = "outside"))), MediaType.APPLICATION_JSON))

        val results = client.fetchActivities()

        assertEquals(2, results.size)
        val titles = results.map { it.title }.toSet()
        assertTrue("공모전A" in titles)
        assertTrue("대외활동B" in titles)
        server.verify()
    }

    @Test
    fun `fetchActivities filters out non-ING and non-INGEND items`() {
        val (client, server) = createClientWithServer()

        val items = listOf(
            sampleItem("1", "접수중", process = "ING"),
            sampleItem("2", "마감임박", process = "INGEND"),
            sampleItem("3", "마감", process = "END"),
            sampleItem("4", "접수예정", process = "YET"),
        )
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/contest/subList.do")))
            .andRespond(withSuccess(buildResponse(items, totalcnt = items.size), MediaType.APPLICATION_JSON))
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/outside/subList.do")))
            .andRespond(withSuccess(buildResponse(emptyList()), MediaType.APPLICATION_JSON))

        val results = client.fetchActivities()

        assertEquals(2, results.size)
        assertEquals(setOf("1", "2"), results.map { it.sourceId }.toSet())
        server.verify()
    }

    @Test
    fun `fetchActivities builds correct URL for each reg_type`() {
        val (client, server) = createClientWithServer()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/contest/subList.do")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem("1", "공모전", regType = "contest"))), MediaType.APPLICATION_JSON))
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/outside/subList.do")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem("2", "대외활동", regType = "outside"))), MediaType.APPLICATION_JSON))

        val results = client.fetchActivities()

        val contestResult = results.find { it.sourceId == "1" }
        val outsideResult = results.find { it.sourceId == "2" }
        assertTrue(contestResult?.url?.contains("/contest/view.do") == true)
        assertTrue(outsideResult?.url?.contains("/outside/view.do") == true)
        server.verify()
    }

    @Test
    fun `fetchActivities deduplicates across endpoints`() {
        val (client, server) = createClientWithServer()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/contest/subList.do")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem("1", "중복항목"))), MediaType.APPLICATION_JSON))
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/outside/subList.do")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem("1", "중복항목", regType = "outside"))), MediaType.APPLICATION_JSON))

        val results = client.fetchActivities()

        assertEquals(1, results.size)
        server.verify()
    }

    @Test
    fun `fetchActivities throws AiServiceException on HTTP error`() {
        val (client, server) = createClientWithServer()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/contest/subList.do")))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertFailsWith<AiServiceException> {
            client.fetchActivities()
        }
        server.verify()
    }

    @Test
    fun `fetchActivities includes prize_money in description`() {
        val (client, server) = createClientWithServer()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/contest/subList.do")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem())), MediaType.APPLICATION_JSON))
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/outside/subList.do")))
            .andRespond(withSuccess(buildResponse(emptyList()), MediaType.APPLICATION_JSON))

        val results = client.fetchActivities()

        assertTrue(results[0].description?.contains("500만원") == true)
        assertTrue(results[0].description?.contains("대학생") == true)
        server.verify()
    }
}
