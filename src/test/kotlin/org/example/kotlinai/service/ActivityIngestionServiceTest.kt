package org.example.kotlinai.service

import org.example.kotlinai.dto.response.ExternalActivityDto
import org.example.kotlinai.entity.ActivityListing
import org.example.kotlinai.entity.IngestionRun
import org.example.kotlinai.repository.ActivityListingRepository
import org.example.kotlinai.repository.IngestionRunRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ActivityIngestionServiceTest {

    private val stubActivities = listOf(
        ExternalActivityDto(
            sourceId = "act-001",
            title = "테스트 공모전",
            organizer = "테스트 주최",
            url = "https://example.com/1",
            category = "IT",
        ),
        ExternalActivityDto(
            sourceId = "act-002",
            title = "테스트 대외활동",
            organizer = "테스트 기관",
            url = "https://example.com/2",
            category = "마케팅",
        ),
    )

    private val mockClient = object : ExternalActivityClient {
        override fun sourceName() = "mock-activity"
        override fun fetchActivities(): List<ExternalActivityDto> = stubActivities
    }

    private val activityListingRepository: ActivityListingRepository = mock()
    private val ingestionRunRepository: IngestionRunRepository = mock()
    private val embeddingService: EmbeddingService = mock()
    private val upstageEmbeddingService: UpstageEmbeddingService = mock()
    private val activityListingSaver: ActivityListingSaver = mock()
    private val embeddingUpdater: EmbeddingUpdater = mock()
    private val service = ActivityIngestionService(
        clients = listOf(mockClient),
        activityListingRepository = activityListingRepository,
        ingestionRunRepository = ingestionRunRepository,
        embeddingService = embeddingService,
        upstageEmbeddingService = upstageEmbeddingService,
        activityListingSaver = activityListingSaver,
        embeddingUpdater = embeddingUpdater,
    )

    private fun stubSave() {
        doAnswer { it.arguments[0] as IngestionRun }
            .whenever(ingestionRunRepository).save(any())
    }

    private fun stubUpsert(existingSourceIds: List<String> = emptyList()) {
        whenever(activityListingRepository.findSourceIdsBySourceName(any())).thenReturn(existingSourceIds)
        doAnswer { it.arguments[0] }.whenever(activityListingSaver).save(any())
    }

    @Test
    fun `unknown source throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            service.runIngestion("unknown-source")
        }
    }

    @Test
    fun `new activities are saved and newCount is correct`() {
        stubSave()
        stubUpsert()

        val results = service.runIngestion("mock-activity")

        assertEquals(1, results.size)
        assertEquals(2, results[0].newCount)
        assertEquals(0, results[0].duplicateCount)
        assertTrue(results[0].success)
    }

    @Test
    fun `existing activities are skipped and duplicateCount is correct`() {
        stubSave()
        stubUpsert(existingSourceIds = listOf("act-001", "act-002"))

        val results = service.runIngestion("mock-activity")

        assertEquals(0, results[0].newCount)
        assertEquals(2, results[0].duplicateCount)
    }

    @Test
    fun `source null runs all clients`() {
        stubSave()
        stubUpsert()

        val results = service.runIngestion(null)

        assertEquals(1, results.size)
        assertEquals("mock-activity", results[0].sourceName)
    }

    @Test
    fun `client throwing exception marks run as failed`() {
        val failingClient = object : ExternalActivityClient {
            override fun sourceName() = "failing"
            override fun fetchActivities(): List<ExternalActivityDto> = throw RuntimeException("boom")
        }
        val failService = ActivityIngestionService(
            clients = listOf(failingClient),
            activityListingRepository = activityListingRepository,
            ingestionRunRepository = ingestionRunRepository,
            embeddingService = embeddingService,
            upstageEmbeddingService = upstageEmbeddingService,
            activityListingSaver = activityListingSaver,
            embeddingUpdater = embeddingUpdater,
        )
        doAnswer { it.arguments[0] as IngestionRun }
            .whenever(ingestionRunRepository).save(any())

        assertFailsWith<RuntimeException> {
            failService.runIngestion("failing")
        }
        verify(ingestionRunRepository, times(2)).save(any())
    }
}
