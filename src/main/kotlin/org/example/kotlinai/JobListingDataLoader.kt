package org.example.kotlinai

import org.example.kotlinai.entity.JobListing
import org.example.kotlinai.repository.JobListingRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class JobListingDataLoader(
    private val jobListingRepository: JobListingRepository,
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (jobListingRepository.count() > 0) return

        val listings = listOf(
            JobListing(
                title = "프론트엔드 개발자 (React)",
                company = "스타트업 A",
                url = "https://example.com/jobs/1",
                description = "React, TypeScript, Next.js를 활용한 웹 프론트엔드 개발. 3년 이상 경력 우대.",
                collectedAt = LocalDateTime.now().minusHours(2),
            ),
            JobListing(
                title = "백엔드 개발자 (Java/Spring)",
                company = "테크 기업 B",
                url = "https://example.com/jobs/2",
                description = "Spring Boot, JPA, MySQL 기반의 서버 개발. MSA 경험자 우대.",
                collectedAt = LocalDateTime.now().minusHours(5),
            ),
            JobListing(
                title = "풀스택 개발자 (Node.js + React)",
                company = "플랫폼 기업 C",
                url = "https://example.com/jobs/3",
                description = "Node.js Express 백엔드와 React 프론트엔드 개발. TypeScript 필수.",
                collectedAt = LocalDateTime.now().minusHours(10),
            ),
            JobListing(
                title = "데이터 엔지니어",
                company = "데이터 회사 D",
                url = "https://example.com/jobs/4",
                description = "Python, Spark, Airflow를 이용한 데이터 파이프라인 구축 및 운영.",
                collectedAt = LocalDateTime.now().minusDays(1),
            ),
            JobListing(
                title = "iOS 개발자 (Swift)",
                company = "모바일 스타트업 E",
                url = "https://example.com/jobs/5",
                description = "Swift, UIKit, SwiftUI를 활용한 iOS 앱 개발. 앱스토어 출시 경험 필수.",
                collectedAt = LocalDateTime.now().minusDays(2),
            ),
        )
        jobListingRepository.saveAll(listings)
    }
}
