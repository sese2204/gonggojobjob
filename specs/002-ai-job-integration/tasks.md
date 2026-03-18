# Tasks: AI Job Matching Integration

**Input**: Design documents from `/specs/002-ai-job-integration/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Config, secrets management, and .gitignore setup before any feature code is written.

- [x] T001 Add `application-local.yml` to `.gitignore` in project root `.gitignore`
- [x] T002 Add Gemini config block to `src/main/resources/application.yml` (`gemini.api.key`, `url`, `timeout-seconds: 30`)
- [x] T003 Create `src/main/resources/application-local.yml` with placeholder `gemini.api.key: REPLACE_ME` (gitignored)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before User Story 1 can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T004 Create `src/main/kotlin/org/example/kotlinai/global/exception/AiServiceException.kt` — custom exception class `class AiServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)`
- [x] T005 Modify `src/main/kotlin/org/example/kotlinai/global/exception/GlobalExceptionHandler.kt` — add `@ExceptionHandler(AiServiceException::class)` handler returning HTTP 503 `ErrorResponse`
- [x] T006 Create `src/main/kotlin/org/example/kotlinai/entity/JobListing.kt` — JPA entity with fields: `id: Long`, `title: String`, `company: String`, `url: String`, `description: String?`, `collectedAt: LocalDateTime`
- [x] T007 Create `src/main/kotlin/org/example/kotlinai/repository/JobListingRepository.kt` — Spring Data JPA interface with `findTop10ByOrderByCollectedAtDesc(): List<JobListing>`

**Checkpoint**: Foundation ready — User Story 1 implementation can now begin.

---

## Phase 3: User Story 1 — AI-Powered Job Match Scoring (Priority: P1) 🎯 MVP

**Goal**: `POST /api/jobs/search` returns AI-generated `match` scores and Korean `reason` strings for each listing retrieved from the DB.

**Independent Test**: Send `POST /api/jobs/search` with `{"tags":["React"],"query":"프론트엔드"}`, verify `match` values are non-uniform and each `reason` is a non-empty Korean sentence.

### Implementation for User Story 1

- [x] T008 [P] [US1] Create `src/main/kotlin/org/example/kotlinai/dto/request/JobSearchRequest.kt` — `data class JobSearchRequest(val tags: List<String> = emptyList(), val query: String = "")`
- [x] T009 [P] [US1] Create `src/main/kotlin/org/example/kotlinai/dto/response/JobResult.kt` — `data class JobResult(val id: String, val title: String, val company: String, val match: Int, val reason: String, val url: String)`
- [x] T010 [P] [US1] Create `src/main/kotlin/org/example/kotlinai/dto/response/JobSearchResponse.kt` — `data class JobSearchResponse(val jobs: List<JobResult>, val totalCount: Int, val newTodayCount: Int)`
- [x] T011 [P] [US1] Create `src/main/kotlin/org/example/kotlinai/dto/response/AiMatchResult.kt` — `data class AiMatchResult(val id: String, val match: Int, val reason: String)` (internal AI result DTO)
- [x] T012 [US1] Create `src/main/kotlin/org/example/kotlinai/service/GeminiService.kt` — `@Service` that (1) reads `gemini.api.key`, `gemini.api.url`, `gemini.api.timeout-seconds` from config, (2) builds a Korean prompt with tags/query/listings, (3) calls Gemini via `RestClient` with `responseMimeType: "application/json"`, (4) parses `candidates[0].content.parts[0].text` as `List<AiMatchResult>`, (5) throws `AiServiceException` on any error (network, timeout, JSON parse failure, empty response)
- [x] T013 [US1] Create `src/main/kotlin/org/example/kotlinai/service/JobSearchService.kt` — `@Service @Transactional(readOnly = true)` that (1) validates input with `require()`, (2) fetches up to 10 listings via `JobListingRepository`, (3) calls `GeminiService`, (4) clamps match scores to 0–100, (5) substitutes fallback Korean reason if AI returns null/blank, (6) sorts by match descending, (7) returns `JobSearchResponse`. Include `fun JobListing.toAiSummary()` and `fun JobListing.toResult(aiResult: AiMatchResult)` extension functions.
- [x] T014 [US1] Create `src/main/kotlin/org/example/kotlinai/controller/JobSearchController.kt` — `@RestController @RequestMapping("/api/jobs")` with `@PostMapping("/search")` that delegates entirely to `JobSearchService`, no business logic
- [x] T015 [US1] Create a startup seed `@Component` (e.g., `JobListingDataLoader.kt` in `src/main/kotlin/org/example/kotlinai/`) implementing `CommandLineRunner` — inserts 5 diverse hardcoded `JobListing` rows (varied titles/companies/descriptions) on startup so AI scoring produces non-uniform results

**Checkpoint**: At this point, User Story 1 is fully functional. Run quickstart.md validation to verify.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Tests, documentation, and validation.

- [x] T016 [P] Create `src/test/kotlin/org/example/kotlinai/service/GeminiServiceTest.kt` — unit test that mocks `RestClient` to return a valid JSON response and verifies (1) correct prompt sent, (2) `List<AiMatchResult>` parsed correctly, (3) `AiServiceException` thrown when HTTP error or invalid JSON returned
- [x] T017 [P] Create `src/test/kotlin/org/example/kotlinai/service/JobSearchServiceTest.kt` — unit test that mocks `GeminiService` and `JobListingRepository`, verifies (1) validation throws `IllegalArgumentException` when both tags/query empty, (2) results sorted by match desc, (3) scores clamped to 0–100, (4) fallback reason applied when AI returns blank
- [x] T018 Create `src/test/kotlin/org/example/kotlinai/controller/JobSearchControllerTest.kt` — `@SpringBootTest` integration test that stubs `GeminiService` via `@MockBean` and calls `POST /api/jobs/search` end-to-end, verifying HTTP 200, response shape, and HTTP 400 on empty input
- [x] T019 Add `GEMINI_API_KEY` GitHub Actions workflow snippet to `quickstart.md` (confirm docs match actual setup)
- [ ] T020 Run `./gradlew bootRun --args='--spring.profiles.active=local'` locally and execute the validation curl from `quickstart.md` to confirm end-to-end AI scoring works

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — **BLOCKS User Story 1**
- **User Story 1 (Phase 3)**: Depends on Phase 2 completion
  - T008–T011 can run in parallel (separate DTO files)
  - T012 (GeminiService) can start after T011 (AiMatchResult DTO)
  - T013 (JobSearchService) depends on T007, T008, T009, T010, T011, T012
  - T014 (Controller) depends on T013
  - T015 (DataLoader) can run in parallel with T012–T014 (independent)
- **Polish (Phase 4)**: Depends on Phase 3 completion

### Parallel Opportunities Within User Story 1

```bash
# After Phase 2 completes, launch these in parallel:
T008: Create JobSearchRequest.kt
T009: Create JobResult.kt
T010: Create JobSearchResponse.kt
T011: Create AiMatchResult.kt
T015: Create JobListingDataLoader.kt

# Then sequentially:
T012: GeminiService (needs AiMatchResult T011)
T013: JobSearchService (needs all DTOs + GeminiService)
T014: JobSearchController (needs JobSearchService)
```

---

## Implementation Strategy

### MVP (User Story 1 Only)

1. Complete Phase 1: Setup (config + .gitignore)
2. Complete Phase 2: Foundational (exception, entity, repository)
3. Complete Phase 3: User Story 1 (DTOs, GeminiService, JobSearchService, Controller, DataLoader)
4. **STOP and VALIDATE**: Run quickstart.md curl command, confirm AI scores are non-uniform and reasons are Korean
5. Proceed to Phase 4: Tests + polish

### Execution Order Summary

| Task | Depends On | Parallel? |
|---|---|---|
| T001–T003 | — | Yes (different files) |
| T004–T005 | T001–T003 | Yes (different files) |
| T006–T007 | T004–T005 | Yes (different files) |
| T008–T011, T015 | T006–T007 | Yes (different files) |
| T012 | T011 | — |
| T013 | T007–T012 | — |
| T014 | T013 | — |
| T016–T017 | T012–T013 | Yes (different files) |
| T018 | T014 | — |
| T019–T020 | T018 | — |

---

## Notes

- [P] tasks touch different files and have no shared dependencies
- Each DTO is a single `data class` — fast to create, safe to parallelize
- `GeminiService` is the highest-risk task (external API + JSON parsing) — implement and test first before wiring into `JobSearchService`
- `application-local.yml` must exist with a real API key before T020 (end-to-end validation)
- Do not commit `application-local.yml` — verify `.gitignore` entry from T001 before adding the key
