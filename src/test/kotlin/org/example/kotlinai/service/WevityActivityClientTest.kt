package org.example.kotlinai.service

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WevityActivityClientTest {

    private val baseUrl = "https://www.wevity.com"

    private fun createClient(pages: Map<String, String>): WevityActivityClient =
        WevityActivityClient(
            baseUrl = baseUrl,
            maxPages = 10,
            pageDelayMs = 0,
            documentFetcher = { url -> Jsoup.parse(pages[url] ?: "<html></html>", baseUrl) },
        )

    private fun contestListHtml(vararg items: Triple<String, String, String>): String {
        val lis = items.joinToString("\n") { (ix, title, statusClass) ->
            """
            <li>
                <div class="tit">
                    <a href="?c=find&s=1&gub=1&gbn=view&gp=1&ix=$ix">$title <span class="stat new">신규</span></a>
                    <div class="sub-tit">분야 : IT/웹</div>
                </div>
                <div class="organ">테스트주최</div>
                <div class="day">
                    D-10
                    <span class="dday $statusClass">접수중</span>
                </div>
            </li>
            """.trimIndent()
        }
        return """
        <html><body>
        <ul class="list">
            <li class="top">헤더</li>
            $lis
        </ul>
        </body></html>
        """.trimIndent()
    }

    private fun activityListHtml(vararg items: Triple<String, String, String>): String {
        val lis = items.joinToString("\n") { (ix, title, dday) ->
            """
            <li>
                <a href="?c=active&s=1&gub=1&gbn=view&gp=1&ix=$ix"><img src="thumb.jpg"/></a>
                <div class="hide-info">
                    <div class="hide-dday">$dday</div>
                    <div class="hide-tit">
                        <a href="?c=active&s=1&gub=1&gbn=view&gp=1&ix=$ix">$title</a>
                    </div>
                    <div class="hide-cat">대외활동/서포터즈</div>
                </div>
            </li>
            """.trimIndent()
        }
        return """
        <html><body>
        <ul class="ext-list">
            $lis
        </ul>
        </body></html>
        """.trimIndent()
    }

    @Test
    fun `sourceName returns wevity`() {
        val client = createClient(emptyMap())
        assertEquals("wevity", client.sourceName())
    }

    @Test
    fun `fetchActivities includes contests with status ing or soon`() {
        val pages = mapOf(
            "$baseUrl/?c=find&s=1&gub=1&gp=1" to contestListHtml(
                Triple("100", "접수중 공모전", "ing"),
                Triple("101", "마감임박 공모전", "soon"),
            ),
            "$baseUrl/?c=find&s=1&gub=1&gp=2" to "<html><body><ul class='list'></ul></body></html>",
            "$baseUrl/?c=active&s=1&gub=1&gp=1" to "<html><body><ul class='ext-list'></ul></body></html>",
        )
        val client = createClient(pages)

        val results = client.fetchActivities()

        assertEquals(2, results.size)
        assertEquals("접수중 공모전", results[0].title)
        assertEquals("마감임박 공모전", results[1].title)
    }

    @Test
    fun `fetchActivities excludes contests with status end`() {
        val pages = mapOf(
            "$baseUrl/?c=find&s=1&gub=1&gp=1" to contestListHtml(
                Triple("100", "접수중", "ing"),
                Triple("101", "마감됨", "end"),
            ),
            "$baseUrl/?c=find&s=1&gub=1&gp=2" to "<html><body><ul class='list'></ul></body></html>",
            "$baseUrl/?c=active&s=1&gub=1&gp=1" to "<html><body><ul class='ext-list'></ul></body></html>",
        )
        val client = createClient(pages)

        val results = client.fetchActivities()

        assertEquals(1, results.size)
        assertEquals("접수중", results[0].title)
    }

    @Test
    fun `fetchActivities includes activities section entries`() {
        val pages = mapOf(
            "$baseUrl/?c=find&s=1&gub=1&gp=1" to "<html><body><ul class='list'></ul></body></html>",
            "$baseUrl/?c=active&s=1&gub=1&gp=1" to activityListHtml(
                Triple("200", "서포터즈 모집", "D-5"),
                Triple("201", "마감 활동", "마감"),
            ),
            "$baseUrl/?c=active&s=1&gub=1&gp=2" to "<html><body><ul class='ext-list'></ul></body></html>",
        )
        val client = createClient(pages)

        val results = client.fetchActivities()

        assertEquals(1, results.size)
        assertEquals("서포터즈 모집", results[0].title)
        assertEquals("", results[0].organizer)
        assertEquals("대외활동/서포터즈", results[0].category)
    }

    @Test
    fun `fetchActivities extracts sourceId from href correctly`() {
        val pages = mapOf(
            "$baseUrl/?c=find&s=1&gub=1&gp=1" to contestListHtml(Triple("99999", "테스트", "ing")),
            "$baseUrl/?c=find&s=1&gub=1&gp=2" to "<html><body><ul class='list'></ul></body></html>",
            "$baseUrl/?c=active&s=1&gub=1&gp=1" to "<html><body><ul class='ext-list'></ul></body></html>",
        )
        val client = createClient(pages)

        val results = client.fetchActivities()

        assertEquals("99999", results[0].sourceId)
        assertTrue(results[0].url.contains("ix=99999"))
    }

    @Test
    fun `fetchActivities returns empty when page selector matches no elements`() {
        val pages = mapOf(
            "$baseUrl/?c=find&s=1&gub=1&gp=1" to "<html><body><ul class='list'></ul></body></html>",
            "$baseUrl/?c=active&s=1&gub=1&gp=1" to "<html><body><ul class='ext-list'></ul></body></html>",
        )
        val client = createClient(pages)

        val results = client.fetchActivities()

        assertTrue(results.isEmpty())
    }
}
