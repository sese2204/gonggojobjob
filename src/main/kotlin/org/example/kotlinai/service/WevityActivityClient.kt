package org.example.kotlinai.service

import org.example.kotlinai.dto.response.ExternalActivityDto
import org.example.kotlinai.global.exception.AiServiceException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class WevityActivityClient(
    @Value("\${wevity.api.base-url}") private val baseUrl: String,
    @Value("\${wevity.api.max-pages:10}") private val maxPages: Int,
    @Value("\${wevity.api.page-delay-ms:500}") private val pageDelayMs: Long,
    private val documentFetcher: (String) -> Document = { url ->
        Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .timeout(10_000)
            .get()
    },
) : ExternalActivityClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val ixRegex = Regex("[?&]ix=(\\d+)")

    override fun sourceName() = "wevity"

    override fun fetchActivities(): List<ExternalActivityDto> {
        log.info("[위비티] 크롤링 시작 — 최대 {}페이지", maxPages)

        return try {
            val contests = fetchContests()
            val activities = fetchActivitiesSection()

            val merged = (contests + activities).distinctBy { it.sourceId }
            log.info("[위비티] 크롤링 완료 — 공모전: {}건, 대외활동: {}건, 통합(중복 제거): {}건",
                contests.size, activities.size, merged.size)
            merged
        } catch (e: AiServiceException) {
            throw e
        } catch (e: Exception) {
            log.error("[위비티] 크롤링 실패 — {}", e.message, e)
            throw AiServiceException("위비티 크롤링 실패: ${e.message}", e)
        }
    }

    private fun fetchContests(): List<ExternalActivityDto> {
        val allContests = mutableListOf<ExternalActivityDto>()
        val seenIds = mutableSetOf<String>()

        for (page in 1..maxPages) {
            val url = "$baseUrl/?c=find&s=1&gub=1&gp=$page"
            val doc = documentFetcher(url)

            val items = doc.select("ul.list li").toList().filter { !it.hasClass("top") }
            if (items.isEmpty()) break

            for (li in items) {
                try {
                    val statusSpan = li.select("div.day span.dday")
                    val statusClass = statusSpan.attr("class")
                    if (!statusClass.contains("ing") && !statusClass.contains("soon")) continue

                    val linkElement = li.select("div.tit > a").first() ?: continue
                    val href = linkElement.attr("href")
                    val sourceId = extractIxParam(href) ?: continue
                    val title = linkElement.ownText().trim()
                    if (title.isBlank()) continue

                    val organizer = li.select("div.organ").text().trim()
                    val categoryRaw = li.select("div.tit div.sub-tit").text().trim()
                    val category = categoryRaw.removePrefix("분야 : ").trim().takeIf { it.isNotBlank() }
                    val dday = li.select("div.day").first()?.ownText()?.trim()

                    val dto = ExternalActivityDto(
                        sourceId = sourceId,
                        title = title,
                        organizer = organizer,
                        url = "$baseUrl/?c=find&s=1&gub=1&gbn=view&ix=$sourceId",
                        category = category,
                        description = dday,
                    )
                    if (seenIds.add(dto.sourceId)) {
                        allContests.add(dto)
                    }
                } catch (e: Exception) {
                    log.warn("[위비티] 공모전 항목 파싱 실패, 건너뜀: {}", e.message, e)
                }
            }

            log.debug("[위비티] 공모전 페이지 {} — {}건 누적", page, allContests.size)
            if (page < maxPages && pageDelayMs > 0) {
                Thread.sleep(pageDelayMs)
            }
        }

        return allContests
    }

    private fun fetchActivitiesSection(): List<ExternalActivityDto> {
        val allActivities = mutableListOf<ExternalActivityDto>()
        val seenIds = mutableSetOf<String>()

        for (page in 1..maxPages) {
            val url = "$baseUrl/?c=active&s=1&gub=1&gp=$page"
            val doc = documentFetcher(url)

            val items = doc.select("ul.ext-list li")
            if (items.isEmpty()) break

            for (li in items) {
                try {
                    val ddayText = li.select("div.hide-dday").text().trim()
                    if (ddayText == "마감" || ddayText.isBlank()) continue

                    val linkElement = li.select("div.hide-tit > a").first() ?: continue
                    val href = linkElement.attr("href")
                    val sourceId = extractIxParam(href) ?: continue
                    val title = linkElement.text().trim()
                    if (title.isBlank()) continue

                    val category = li.select("div.hide-cat").text().trim().takeIf { it.isNotBlank() }

                    val dto = ExternalActivityDto(
                        sourceId = sourceId,
                        title = title,
                        organizer = "",
                        url = "$baseUrl/?c=active&s=1&gub=1&gbn=view&ix=$sourceId",
                        category = category,
                        description = ddayText,
                    )
                    if (seenIds.add(dto.sourceId)) {
                        allActivities.add(dto)
                    }
                } catch (e: Exception) {
                    log.warn("[위비티] 대외활동 항목 파싱 실패, 건너뜀: {}", e.message, e)
                }
            }

            log.debug("[위비티] 대외활동 페이지 {} — {}건 누적", page, allActivities.size)
            if (page < maxPages && pageDelayMs > 0) {
                Thread.sleep(pageDelayMs)
            }
        }

        return allActivities
    }

    private fun extractIxParam(href: String): String? =
        ixRegex.find(href)?.groupValues?.get(1)
}
