# Implementation Plan: AI Job Matching Integration

**Branch**: `002-ai-job-integration` | **Date**: 2026-03-18 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-ai-job-integration/spec.md`

## Summary

Replace the placeholder `match`/`reason` values in `POST /api/jobs/search` with real
AI-generated scores and Korean explanations. The backend retrieves up to 10 job listings
from H2, sends them to Google Gemini in a single batch request with JSON structured output,
parses the response, and returns sorted results. API contract from spec 001 is preserved.
API key is managed via `application-local.yml` locally and GitHub Secrets in CI.

## Technical Context

**Language/Version**: Kotlin 1.9.25 on JVM 21
**Primary Dependencies**: Spring Boot 3.5.11, Spring Data JPA, H2 (dev), `RestClient` (Spring Web), Jackson
**Storage**: H2 in-memory (dev/test); seeded with 5 hardcoded `JobListing` rows on startup
**Testing**: JUnit 5 + Spring Boot Test (`@SpringBootTest` for integration, unit tests for service logic)
**Target Platform**: JVM server (local + GitHub Actions CI)
**Project Type**: REST web service
**Performance Goals**: Search response within 35 seconds (30s Gemini timeout + 5s overhead)
**Constraints**: No new library dependencies; API key never in version control
**Scale/Scope**: Single-user test iteration; 10 listings max per AI call

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Layered Architecture | PASS | `JobSearchController` → `JobSearchService` → `JobListingRepository` → `JobListing`. `GeminiService` is a service-layer collaborator, not a controller. |
| II. DTO Separation | PASS | `JobSearchRequest`, `JobSearchResponse`, `JobResult` are pure `data class` types; no JPA annotations. `JobListing.toResult()` extension function in service. |
| III. Transaction Convention | PASS | `JobSearchService` declares `@Transactional(readOnly = true)` at class level. No write operations in search flow. |
| IV. Centralized Error Handling | PASS | `AiServiceException` added to `GlobalExceptionHandler` → HTTP 503. `IllegalArgumentException` (empty input) already handled → HTTP 400. |
| V. Kotlin Idioms | PASS | `require()` for input validation, expression bodies on single-expression functions, `data class` for all DTOs. |

**Post-design re-check**: All principles maintained. No violations.

## Project Structure

### Documentation (this feature)

```text
specs/002-ai-job-integration/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/
│   └── job-search-api.md
└── tasks.md             ← Phase 2 output (created by /speckit.tasks)
```

### Source Code (new/modified files)

```text
src/main/kotlin/org/example/kotlinai/
├── controller/
│   └── JobSearchController.kt          [NEW] POST /api/jobs/search
├── service/
│   ├── JobSearchService.kt             [NEW] orchestrates DB fetch + AI call
│   └── GeminiService.kt                [NEW] RestClient call to Gemini API
├── repository/
│   └── JobListingRepository.kt         [NEW] Spring Data JPA
├── entity/
│   └── JobListing.kt                   [NEW] job posting entity
├── dto/
│   ├── request/
│   │   └── JobSearchRequest.kt         [NEW]
│   └── response/
│       ├── JobSearchResponse.kt        [NEW]
│       ├── JobResult.kt                [NEW]
│       ├── AiMatchRequest.kt           [NEW] internal AI payload DTOs
│       └── AiMatchResult.kt            [NEW] internal AI result DTO
└── global/
    └── exception/
        ├── GlobalExceptionHandler.kt   [MODIFY] add AiServiceException → 503
        └── AiServiceException.kt       [NEW]

src/main/resources/
├── application.yml                     [MODIFY] add gemini.api config block
└── application-local.yml               [NEW, gitignored] local API key override

src/test/kotlin/org/example/kotlinai/
├── service/
│   ├── JobSearchServiceTest.kt         [NEW] unit tests
│   └── GeminiServiceTest.kt            [NEW] unit tests (mock HTTP)
└── controller/
    └── JobSearchControllerTest.kt      [NEW] @SpringBootTest integration test

.gitignore                              [MODIFY] add application-local.yml
```

**Structure Decision**: Single Spring Boot project — all new code under existing
`org.example.kotlinai` package following the established four-layer structure.
