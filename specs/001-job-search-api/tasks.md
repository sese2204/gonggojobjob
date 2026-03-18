---
description: "Task list for Job Search API — 001-job-search-api"
---

# Tasks: Job Search API

**Input**: Design documents from `/specs/001-job-search-api/`
**Prerequisites**: plan.md ✅, spec.md ✅, data-model.md ✅, contracts/ ✅

**Scope note**: Per user instruction, focus is on job data ingestion (fetch from external API +
save to DB). Search endpoint returns recent jobs without AI matching. Matching logic is deferred
to AI integration phase.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[US1]**: Job Data Ingestion, **[US2]**: Job Search Endpoint

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: No new project initialization needed — existing Spring Boot setup is reused.
Verify package layout and confirm H2 config is sufficient.

- [x] T001 Confirm H2 console is enabled in `src/main/resources/application.properties` for local data inspection during development

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core entity and cross-cutting config that BOTH user stories depend on.

**⚠️ CRITICAL**: Neither US1 nor US2 can begin until this phase is complete.

- [x] T002 Create `Job` entity in `src/main/kotlin/org/example/kotlinai/entity/Job.kt` with fields: `id` (Long, PK, IDENTITY), `title` (String, NOT NULL), `company` (String, NOT NULL), `url` (String, NOT NULL, TEXT), `collectedAt` (LocalDateTime, NOT NULL, updatable=false, default=now())
- [x] T003 [P] Create `JobRepository` interface in `src/main/kotlin/org/example/kotlinai/repository/JobRepository.kt` extending `JpaRepository<Job, Long>` with methods: `findTop10ByOrderByCollectedAtDesc()`, `countByCollectedAtAfter(since: LocalDateTime)`
- [x] T004 [P] Create `CorsConfig` in `src/main/kotlin/org/example/kotlinai/global/config/CorsConfig.kt` implementing `WebMvcConfigurer`, allowing origin `http://localhost:5173`, methods POST and GET, all headers

**Checkpoint**: Entity + repository + CORS ready — US1 and US2 can now start.

---

## Phase 3: User Story 1 — Job Data Ingestion (Priority: P1) 🎯 MVP

**Goal**: Fetch job listings from an external API and persist them to the internal DB.
This is the data supply layer that US2 depends on for meaningful results.

**Independent Test**: POST `/api/jobs/ingest` → verify rows appear in H2 console
(`SELECT * FROM jobs`) and `collectedAt` is populated.

### Implementation for User Story 1

- [x] T005 [P] [US1] Create `ExternalJobDto` data class in `src/main/kotlin/org/example/kotlinai/dto/response/ExternalJobDto.kt` representing the raw response shape from an external job board API: `title` (String), `company` (String), `url` (String)
- [x] T006 [P] [US1] Create `ExternalJobClient` interface in `src/main/kotlin/org/example/kotlinai/service/ExternalJobClient.kt` with a single method `fetchJobs(): List<ExternalJobDto>` — interface allows swapping real API client with stub
- [x] T007 [US1] Create `StubExternalJobClient` in `src/main/kotlin/org/example/kotlinai/service/StubExternalJobClient.kt` implementing `ExternalJobClient`, annotated `@Primary @Service`, returning a hardcoded list of 3–5 `ExternalJobDto` instances (enables end-to-end testing without real API keys)
- [x] T008 [US1] Implement `JobIngestionService` in `src/main/kotlin/org/example/kotlinai/service/JobIngestionService.kt` with method `ingestJobs()`: calls `ExternalJobClient.fetchJobs()`, maps each result to a `Job` entity, saves via `JobRepository.saveAll()`, returns count of saved jobs. Annotate class with `@Transactional(readOnly = true)`, annotate `ingestJobs()` with `@Transactional`
- [x] T009 [US1] Create `JobIngestionRequest` data class in `src/main/kotlin/org/example/kotlinai/dto/request/JobIngestionRequest.kt` (empty body for now — extensible for future source/filter params)
- [x] T010 [US1] Implement `JobIngestionController` in `src/main/kotlin/org/example/kotlinai/controller/JobIngestionController.kt` with `POST /api/jobs/ingest` endpoint, `@ResponseStatus(HttpStatus.OK)`, returning count of ingested jobs as `JobIngestionResponse(count: Int)`
- [x] T011 [P] [US1] Create `JobIngestionResponse` data class in `src/main/kotlin/org/example/kotlinai/dto/response/JobIngestionResponse.kt` with field `count: Int`

**Checkpoint**: At this point, calling `POST /api/jobs/ingest` populates the `jobs` table.
Verify in H2 console before proceeding to US2.

---

## Phase 4: User Story 2 — Job Search Endpoint (Priority: P2)

**Goal**: Expose `POST /api/jobs/search` that returns the 10 most recently collected listings
from the internal DB. Match score is a placeholder (`0`). Reason is rule-based template.

**Independent Test**: POST `/api/jobs/search` with `{ "tags": ["React"], "query": "" }` →
verify response has `jobs` array (up to 10), `totalCount`, `newTodayCount`, each job has
`id`, `title`, `company`, `match: 0`, `reason` (non-empty), `url`.

### Implementation for User Story 2

- [x] T012 [P] [US2] Create `JobSearchRequest` data class in `src/main/kotlin/org/example/kotlinai/dto/request/JobSearchRequest.kt` with fields: `tags: List<String> = emptyList()`, `query: String = ""`
- [x] T013 [P] [US2] Create `JobResponse` and `JobSearchResponse` data classes in `src/main/kotlin/org/example/kotlinai/dto/response/JobSearchResponse.kt`: `JobResponse(id: String, title: String, company: String, match: Int, reason: String, url: String)`, `JobSearchResponse(jobs: List<JobResponse>, totalCount: Long, newTodayCount: Long)`
- [x] T014 [US2] Implement `JobService` in `src/main/kotlin/org/example/kotlinai/service/JobService.kt` (class-level `@Transactional(readOnly = true)`): method `searchJobs(request: JobSearchRequest): JobSearchResponse` — validate with `require(request.tags.isNotEmpty() || request.query.isNotBlank()) { "태그 또는 검색어를 입력해주세요" }`, fetch top 10 via `findTop10ByOrderByCollectedAtDesc()`, build `JobSearchResponse` with `totalCount = count()` and `newTodayCount = countByCollectedAtAfter(today midnight)`; add extension function `fun Job.toResponse(reason: String): JobResponse` in same file
- [x] T015 [US2] Add reason generation private function `buildReason(tags: List<String>, query: String): String` to `JobService.kt` following the template logic in `data-model.md`: tags-only / query-only / both cases
- [x] T016 [US2] Implement `JobController` in `src/main/kotlin/org/example/kotlinai/controller/JobController.kt` with `POST /api/jobs/search` mapped to `@PostMapping("/search")` under `@RequestMapping("/api/jobs")`, delegating to `jobService.searchJobs(request)`, returning `JobSearchResponse`

**Checkpoint**: At this point, US1 + US2 are both independently functional. Run the
quickstart.md curl commands to validate end-to-end flow.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Verify cross-cutting behavior and prepare for AI integration handoff.

- [x] T017 [P] Verify `GlobalExceptionHandler` in `src/main/kotlin/org/example/kotlinai/global/exception/GlobalExceptionHandler.kt` already handles `IllegalArgumentException` → HTTP 400 (no code change needed, just confirm and document)
- [x] T018 Run `quickstart.md` validation: (1) POST /api/jobs/ingest → check count > 0, (2) POST /api/jobs/search with valid body → check jobs array, (3) POST /api/jobs/search with empty body → check HTTP 400, (4) verify CORS from browser console
- [x] T019 [P] Add `# TODO: Replace StubExternalJobClient with real API client` comment block to `src/main/kotlin/org/example/kotlinai/service/StubExternalJobClient.kt` listing required changes for real API integration (base URL, auth headers, response mapping)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS both user stories
- **US1 (Phase 3)**: Depends on Phase 2 — no dependency on US2
- **US2 (Phase 4)**: Depends on Phase 2 — no dependency on US1 (but benefits from US1 data)
- **Polish (Phase 5)**: Depends on US1 + US2 both complete

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational — no US2 dependency
- **US2 (P2)**: Can start after Foundational — US2 is independently testable with empty DB

### Within Each User Story

- Interfaces before implementations (T006 before T007, T007 before T008)
- DTOs before services (T005 before T008, T012/T013 before T014)
- Services before controllers (T008 before T010, T014 before T016)

### Parallel Opportunities

```bash
# Phase 2 — run in parallel:
Task: "T003 Create JobRepository"
Task: "T004 Create CorsConfig"

# Phase 3 — run in parallel first:
Task: "T005 Create ExternalJobDto"
Task: "T006 Create ExternalJobClient interface"
# Then T007 → T008 → T009/T010/T011

# Phase 4 — run in parallel first:
Task: "T012 Create JobSearchRequest"
Task: "T013 Create JobResponse + JobSearchResponse"
# Then T014 → T015 → T016
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002, T003, T004)
3. Complete Phase 3: US1 Ingestion (T005–T011)
4. **STOP and VALIDATE**: Call `POST /api/jobs/ingest`, confirm data in DB
5. Proceed to US2 once ingestion is verified

### Incremental Delivery

1. Foundation → trigger ingestion → see data in DB (MVP)
2. Add search endpoint → return recent jobs from DB
3. Swap `StubExternalJobClient` → real API client (next iteration)
4. Replace placeholder match/reason → AI integration (next phase)

### AI Integration Handoff

When AI integration is ready:
1. Implement a real `ExternalJobClient` (remove `@Primary` from stub)
2. Replace `match: 0` with AI-computed score in `JobService.searchJobs()`
3. Replace `buildReason()` template with AI-generated text
4. `JobSearchRequest.tags` and `query` become inputs to AI ranking model

---

## Notes

- [P] tasks = different files, no shared state — safe to run in parallel
- `StubExternalJobClient` with `@Primary` ensures the ingestion pipeline is testable
  without real API credentials
- `match: 0` and `buildReason()` are explicit placeholders — clearly marked for AI replacement
- Commit after each phase checkpoint to enable clean rollback
- Do NOT implement filtering/scoring logic — that belongs to the AI integration phase
