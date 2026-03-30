package org.example.kotlinai.service

import org.example.kotlinai.dto.response.ExternalJobDto
import org.example.kotlinai.entity.IngestionRun
import org.example.kotlinai.repository.IngestionRunRepository
import org.example.kotlinai.repository.JobListingRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JobIngestionServiceTest {

    private val mockClient = StubExternalJobClient()
    private val jobListingRepository: JobListingRepository = mock()
    private val ingestionRunRepository: IngestionRunRepository = mock()
    private val embeddingService: EmbeddingService = mock()
    private val jobListingSaver: JobListingSaver = mock()
    private val service = JobIngestionService(
        clients = listOf(mockClient),
        jobListingRepository = jobListingRepository,
        ingestionRunRepository = ingestionRunRepository,
        embeddingService = embeddingService,
        jobListingSaver = jobListingSaver,
    )

    private fun stubSave() {
        doAnswer { it.arguments[0] as IngestionRun }
            .whenever(ingestionRunRepository).save(any())
    }

    private fun stubUpsert(existingSourceIds: List<String> = emptyList()) {
        whenever(jobListingRepository.findSourceIdsBySourceName(any())).thenReturn(existingSourceIds)
        doAnswer { it.arguments[0] }.whenever(jobListingSaver).save(any())
    }

    @Test
    fun `unknown source throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            service.runIngestion("unknown-source")
        }
    }

    @Test
    fun `new jobs are saved and newCount is correct`() {
        stubSave()
        stubUpsert()

        val results = service.runIngestion("mock")

        assertEquals(1, results.size)
        assertEquals(2, results[0].newCount)
        assertEquals(0, results[0].duplicateCount)
        assertTrue(results[0].success)
    }

    @Test
    fun `existing jobs are skipped and duplicateCount is correct`() {
        stubSave()
        stubUpsert(existingSourceIds = listOf("mock-001", "mock-002"))

        val results = service.runIngestion("mock")

        assertEquals(0, results[0].newCount)
        assertEquals(2, results[0].duplicateCount)
    }

    @Test
    fun `source name null runs all clients`() {
        stubSave()
        stubUpsert()

        val results = service.runIngestion(null)

        assertEquals(1, results.size)
        assertEquals("mock", results[0].sourceName)
    }

    @Test
    fun `client throwing exception marks run as failed`() {
        val failingClient = object : ExternalJobClient {
            override fun sourceName() = "failing"
            override fun fetchJobs(): List<ExternalJobDto> = throw RuntimeException("boom")
        }
        val failService = JobIngestionService(
            clients = listOf(failingClient),
            jobListingRepository = jobListingRepository,
            ingestionRunRepository = ingestionRunRepository,
            embeddingService = embeddingService,
            jobListingSaver = jobListingSaver,
        )
        doAnswer { it.arguments[0] as IngestionRun }
            .whenever(ingestionRunRepository).save(any())

        assertFailsWith<RuntimeException> {
            failService.runIngestion("failing")
        }
        verify(ingestionRunRepository, times(2)).save(any())
    }
}
