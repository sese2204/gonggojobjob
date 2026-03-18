# Implementation Plan: Job Search API

**Branch**: `001-job-search-api` | **Date**: 2026-03-18 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-job-search-api/spec.md`

## Summary

Expose `POST /api/jobs/search` that accepts tags and a natural language query, then returns
the 10 most recently collected job listings from the internal DB. Match scores and semantic
ranking are placeholders for this iteration (deferred to AI integration). CORS must allow
`http://localhost:5173` for the frontend dev server.

## Technical Context

**Language/Version**: Kotlin 1.9.25 / JVM 21 (existing)
**Primary Dependencies**: Spring Boot 3.x, Spring Data JPA, springdoc-openapi (existing)
**Storage**: H2 in-memory (dev) — existing setup, no changes
**Testing**: Spring Boot Test + JUnit 5 (existing)
**Target Platform**: Web service (REST API)
**Project Type**: web-service
**Performance Goals**: Response within 3 seconds (spec SC-002); simple DB query makes this trivial
**Constraints**: No external API calls at search time; match/reason are placeholders
**Scale/Scope**: Single endpoint, single entity — minimal scope

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Layered Architecture | ✅ PASS | `JobController → JobService → JobRepository → Job` |
| II. DTO Separation | ✅ PASS | `JobSearchRequest`, `JobResponse`, `JobSearchResponse` as `data class` |
| III. Transaction Convention | ✅ PASS | Service class-level `readOnly=true`; no write operations in search |
| IV. Centralized Error Handling | ✅ PASS | `require()` for FR-007 (400); existing `GlobalExceptionHandler` handles it |
| V. Kotlin Idioms | ✅ PASS | `require()`, expression bodies, `fun Job.toResponse()` extension function |

**Post-Design Re-check**: All gates clear. New `global/config/CorsConfig.kt` does not
introduce a new top-level package (extends existing `global/` namespace). ✅

## Project Structure

### Documentation (this feature)

```text
specs/001-job-search-api/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── post-jobs-search.md
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/kotlin/org/example/kotlinai/
├── controller/
│   └── JobController.kt          ← POST /api/jobs/search
├── service/
│   └── JobService.kt             ← business logic + Job.toResponse() extension
├── repository/
│   └── JobRepository.kt          ← findTop10ByOrderByCollectedAtDesc()
├── entity/
│   └── Job.kt                    ← @Entity with id, title, company, url, collectedAt
├── dto/
│   ├── request/
│   │   └── JobSearchRequest.kt   ← tags: List<String>, query: String
│   └── response/
│       └── JobSearchResponse.kt  ← JobResponse + JobSearchResponse data classes
└── global/
    └── config/
        └── CorsConfig.kt         ← WebMvcConfigurer allowing localhost:5173
```

**Structure Decision**: Single-project layout extending existing `src/` structure.
All new files follow existing naming and package conventions exactly.

## Complexity Tracking

> No constitution violations — table omitted.
