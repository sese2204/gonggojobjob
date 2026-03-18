package org.example.kotlinai.service

import org.example.kotlinai.dto.response.ExternalJobDto
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

// TODO: Replace StubExternalJobClient with real API client
// Required changes for real API integration:
//   1. Remove @Primary annotation from this class
//   2. Create a new @Service implementation (e.g., SaraminJobClient)
//   3. Inject base URL and API key via @Value from application.yml
//   4. Use RestTemplate or WebClient to call the external API
//   5. Map the external API response JSON to List<ExternalJobDto>
@Primary
@Service
class StubExternalJobClient : ExternalJobClient {

    override fun fetchJobs(): List<ExternalJobDto> = listOf(
        ExternalJobDto(
            title = "백엔드 개발자 (Java/Spring)",
            company = "스타트업 A",
            url = "https://example.com/jobs/1",
        ),
        ExternalJobDto(
            title = "프론트엔드 개발자 (React)",
            company = "테크 컴퍼니 B",
            url = "https://example.com/jobs/2",
        ),
        ExternalJobDto(
            title = "풀스택 개발자 (Node.js + React)",
            company = "이커머스 C",
            url = "https://example.com/jobs/3",
        ),
        ExternalJobDto(
            title = "DevOps 엔지니어 (AWS/Kubernetes)",
            company = "클라우드 서비스 D",
            url = "https://example.com/jobs/4",
        ),
        ExternalJobDto(
            title = "데이터 엔지니어 (Python/Spark)",
            company = "데이터 플랫폼 E",
            url = "https://example.com/jobs/5",
        ),
    )
}
