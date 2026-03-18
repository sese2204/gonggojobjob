package org.example.kotlinai.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.example.kotlinai.global.exception.AiServiceException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeminiServiceTest {

    private lateinit var geminiService: GeminiService
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        geminiService = GeminiService(objectMapper)
    }

    @Test
    fun `extractJsonText returns text from valid Gemini response`() {
        val response = mapOf(
            "candidates" to listOf(
                mapOf(
                    "content" to mapOf(
                        "parts" to listOf(
                            mapOf("text" to """[{"id":"1","match":85,"reason":"React 기술이 일치합니다."}]""")
                        )
                    )
                )
            )
        )

        val method = GeminiService::class.java.getDeclaredMethod("extractJsonText", Map::class.java)
        method.isAccessible = true
        val result = method.invoke(geminiService, response) as? String

        assertNotNull(result)
        assertEquals("""[{"id":"1","match":85,"reason":"React 기술이 일치합니다."}]""", result)
    }

    @Test
    fun `matchJobs throws AiServiceException when apiKey is not set`() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "")
        ReflectionTestUtils.setField(geminiService, "apiUrl", "https://invalid.example.com")
        ReflectionTestUtils.setField(geminiService, "timeoutSeconds", 5L)

        val listings = listOf(AiJobSummary("1", "개발자", "회사A", null))

        assertThrows<AiServiceException> {
            geminiService.matchJobs(listOf("React"), "프론트엔드", listings)
        }
    }

    @Test
    fun `buildPrompt includes tags and query`() {
        val method = GeminiService::class.java.getDeclaredMethod(
            "buildPrompt", List::class.java, String::class.java, List::class.java
        )
        method.isAccessible = true

        val tags = listOf("React", "Node.js")
        val query = "프론트엔드 개발자"
        val listings = listOf(AiJobSummary("1", "웹 개발자", "회사A", "React 개발"))

        val prompt = method.invoke(geminiService, tags, query, listings) as String

        assert(prompt.contains("React")) { "Prompt should contain tag" }
        assert(prompt.contains("프론트엔드 개발자")) { "Prompt should contain query" }
        assert(prompt.contains("한국어")) { "Prompt should request Korean output" }
    }
}
