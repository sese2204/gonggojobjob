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

class AllConActivityClientTest {

    private val objectMapper = jacksonObjectMapper()
    private val baseUrl = "https://www.all-con.co.kr/page/ajax.contest_list.php"

    private fun createClientWithServer(
        maxPages: Int = 20,
        pageDelayMs: Long = 0,
    ): Pair<AllConActivityClient, MockRestServiceServer> {
        val restClientBuilder = RestClient.builder().baseUrl(baseUrl)
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()

        val client = AllConActivityClient(
            apiUrl = baseUrl,
            maxPages = maxPages,
            pageDelayMs = pageDelayMs,
            pageSize = 15,
        )

        val restClientField = AllConActivityClient::class.java.getDeclaredField("restClient\$delegate")
        restClientField.isAccessible = true
        restClientField.set(client, lazyOf(restClientBuilder.build()))

        return client to server
    }

    private fun buildResponse(
        items: List<AllConItem>,
        totalPage: Int = 1,
    ): String = objectMapper.writeValueAsString(
        AllConResponse(totalPage = totalPage, totalCount = items.size, rows = items),
    )

    private fun sampleItem(
        srl: String = "12345",
        title: String = "테스트 공모전",
        host: String = "테스트 기관",
        status: String = "접수중 D-10",
    ) = AllConItem(
        cl_srl = srl,
        cl_title = title,
        cl_host = host,
        cl_cate = "디자인/일반",
        cl_target = "대학생",
        cl_start_date = "26.03.01",
        cl_end_date = "26.04.30",
        cl_status = status,
    )

    @Test
    fun `sourceName returns allcon`() {
        val (client, _) = createClientWithServer()
        assertEquals("allcon", client.sourceName())
    }

    @Test
    fun `fetchActivities filters out 마감 items and keeps 접수중 and 마감임박`() {
        val (client, server) = createClientWithServer()

        val items = listOf(
            sampleItem("1", "접수중 공모전", status = "접수중 D-10"),
            sampleItem("2", "마감임박 공모전", status = "마감임박 D-2"),
            sampleItem("3", "마감 공모전", status = "마감"),
        )
        server.expect(requestTo(org.hamcrest.Matchers.containsString("page=1")))
            .andRespond(withSuccess(buildResponse(items), MediaType.APPLICATION_JSON))

        val results = client.fetchActivities()

        assertEquals(2, results.size)
        assertEquals("접수중 공모전", results[0].title)
        assertEquals("마감임박 공모전", results[1].title)
        server.verify()
    }

    @Test
    fun `fetchActivities maps fields to ExternalActivityDto correctly`() {
        val (client, server) = createClientWithServer()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("page=1")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem())), MediaType.APPLICATION_JSON))

        val results = client.fetchActivities()

        assertEquals(1, results.size)
        val dto = results[0]
        assertEquals("12345", dto.sourceId)
        assertEquals("테스트 공모전", dto.title)
        assertEquals("테스트 기관", dto.organizer)
        assertEquals("https://www.all-con.co.kr/view/contest/12345", dto.url)
        assertEquals("디자인/일반", dto.category)
        assertEquals("26.03.01", dto.startDate)
        assertEquals("26.04.30", dto.endDate)
        server.verify()
    }

    @Test
    fun `fetchActivities paginates until totalPage`() {
        val (client, server) = createClientWithServer()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("page=1")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem("1", "첫째")), totalPage = 2), MediaType.APPLICATION_JSON))
        server.expect(requestTo(org.hamcrest.Matchers.containsString("page=2")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem("2", "둘째")), totalPage = 2), MediaType.APPLICATION_JSON))

        val results = client.fetchActivities()

        assertEquals(2, results.size)
        server.verify()
    }

    @Test
    fun `fetchActivities stops at maxPages`() {
        val (client, server) = createClientWithServer(maxPages = 1)

        server.expect(requestTo(org.hamcrest.Matchers.containsString("page=1")))
            .andRespond(withSuccess(buildResponse(listOf(sampleItem()), totalPage = 5), MediaType.APPLICATION_JSON))

        val results = client.fetchActivities()

        assertEquals(1, results.size)
        server.verify()
    }

    @Test
    fun `fetchActivities throws AiServiceException on HTTP error`() {
        val (client, server) = createClientWithServer()

        server.expect(requestTo(org.hamcrest.Matchers.containsString("page=1")))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertFailsWith<AiServiceException> {
            client.fetchActivities()
        }
        server.verify()
    }

    @Test
    fun `fetchActivities skips items with blank required fields`() {
        val (client, server) = createClientWithServer()

        val items = listOf(
            sampleItem("1", "유효한 공모전"),
            sampleItem("", "ID없음"),
            AllConItem(cl_srl = "2", cl_title = "", cl_status = "접수중"),
        )
        server.expect(requestTo(org.hamcrest.Matchers.containsString("page=1")))
            .andRespond(withSuccess(buildResponse(items), MediaType.APPLICATION_JSON))

        val results = client.fetchActivities()

        assertEquals(1, results.size)
        assertEquals("유효한 공모전", results[0].title)
        server.verify()
    }
}
