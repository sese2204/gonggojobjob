package org.example.kotlinai.service

import org.example.kotlinai.dto.response.ExternalJobDto

// Test fixture only — NOT a @Service bean.
// Instantiate manually in tests to verify ExternalJobClient extensibility.
class StubExternalJobClient : ExternalJobClient {

    override fun sourceName() = "mock"

    override fun fetchJobs(): List<ExternalJobDto> = listOf(
        ExternalJobDto(
            sourceId = "mock-001",
            title = "백엔드 개발자 (Java/Spring)",
            company = "스타트업 A",
            url = "https://example.com/jobs/1",
        ),
        ExternalJobDto(
            sourceId = "mock-002",
            title = "프론트엔드 개발자 (React)",
            company = "테크 컴퍼니 B",
            url = "https://example.com/jobs/2",
        ),
    )
}
