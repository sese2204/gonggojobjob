# Feature Specification: AI Job Matching Integration

**Feature Branch**: `002-ai-job-integration`
**Created**: 2026-03-18
**Status**: Draft
**Input**: User description: "이제 ai를 붙이는 작업을 할거야 추후에는 DB에 공고를 벡터 임베딩으로 저장하고 유사도 검색으로 구현할건데 이번에는 ai를 붙이고 테스트하는 정도로만 하자 공고를 적당히 넘기고 답변을 받는 정도로"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - AI-Powered Job Match Scoring (Priority: P1)

A job seeker sends a search request with tags and/or a natural language query. The backend
retrieves job listings from the internal database and passes them to an AI service along with
the user's search criteria. The AI returns a match score (0–100) and a human-readable reason
for each listing. The search response is identical in shape to spec 001 but now contains
AI-generated match scores and reasons instead of placeholder values.

**Why this priority**: This is the sole purpose of this feature — replacing placeholder values
with real AI-generated content. Everything else is unchanged from spec 001.

**Independent Test**: Can be fully tested by sending POST /api/jobs/search and verifying that
(1) the returned `match` values are non-uniform (actual scoring, not all the same placeholder),
and (2) each `reason` field contains a coherent sentence about why the listing matches
the query — not a template string.

**Acceptance Scenarios**:

1. **Given** a user sends `{ "tags": ["React", "Node.js"], "query": "프론트엔드 개발자" }`,
   **When** the backend receives the request,
   **Then** the response contains a `jobs` array where each item has an AI-generated `match`
   score (0–100) and a non-empty, human-readable `reason` string that references the user's
   criteria.

2. **Given** listings are retrieved from the internal database (up to 10),
   **When** those listings are sent to the AI service along with the user's query and tags,
   **Then** the AI returns a score and reason for every listing, and the response is sorted by
   `match` descending.

3. **Given** the AI service is unavailable or returns an error,
   **When** the backend processes the search request,
   **Then** the system returns HTTP 503 with an error message instead of silently returning
   empty or placeholder results.

4. **Given** a user sends a valid request,
   **When** the AI service responds slowly,
   **Then** the backend waits up to a configurable timeout before returning an error — it does
   not return partial results.

---

### Edge Cases

- What happens if the internal database has fewer than 10 listings? System MUST pass all
  available listings to the AI and return the full AI-scored set (no minimum required).
- What happens if the AI returns a score outside 0–100? System MUST clamp the value to the
  valid range before including it in the response.
- What happens if the AI returns an empty or null `reason` for a listing? System MUST substitute
  a generic fallback reason (e.g., "AI 분석 결과 관련 공고입니다") rather than returning a
  null/empty field.
- What happens if the AI takes longer than the configured timeout? System MUST return HTTP 503
  with a descriptive timeout error — no partial results, no silent fallback to placeholders.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The existing POST /api/jobs/search endpoint MUST now return AI-generated `match`
  scores and `reason` strings instead of placeholder values. The response contract (fields and
  HTTP status codes) from spec 001 is unchanged.
- **FR-002**: The backend MUST retrieve job listings from the internal database (up to 10, most
  recently collected) before invoking the AI service.
- **FR-003**: The backend MUST send the retrieved listings along with the user's `tags` and
  `query` to the AI service in a single batch request per search call.
- **FR-004**: The backend MUST instruct the AI service to return results as a structured JSON
  array (`[{"id": "...", "match": ..., "reason": "..."}]`). The response MUST be parseable
  directly as JSON without regex or free-text extraction. The backend MUST sort the parsed
  results by `match` descending before returning.
- **FR-005**: If the AI service is unavailable, returns an error, or returns a response that
  cannot be parsed as valid JSON, the system MUST immediately return HTTP 503 — no retries,
  no fallback to placeholder values.
- **FR-006**: The system MUST enforce a configurable timeout on the AI service call.
  Default timeout is 30 seconds.
- **FR-007**: If the AI returns a score outside 0–100, the backend MUST clamp it to the valid
  range before including it in the response.
- **FR-008**: If the AI returns a null or empty `reason` for any listing, the backend MUST
  substitute a generic fallback reason string in Korean rather than returning null.
- **FR-010**: The AI service MUST be instructed to generate all `reason` strings in Korean,
  regardless of the language used in the user's `tags` or `query`.
- **FR-009**: The AI integration MUST be testable in isolation — a developer MUST be able to
  invoke the AI service layer directly (e.g., via a unit test or dedicated test endpoint)
  without triggering the full search pipeline.

### Key Entities

- **AiMatchRequest**: Payload sent to the AI service — contains the user's `tags`, `query`,
  and a list of job listing summaries (id, title, company, and any available description).
- **AiMatchResult**: Per-listing result returned by the AI service — `id` (to correlate back
  to the listing), `match` (integer 0–100), `reason` (string).
- **JobListing** (extended from spec 001): Same DB entity. No new fields required for this
  iteration. The `description` field, if present, SHOULD be included in the AI payload to
  improve match quality, but its absence MUST NOT block the AI call.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every job listing in the search response has a unique, non-template `reason`
  string — no two listings in the same response share the exact same reason text.
- **SC-002**: The `match` scores in a response are not all identical — at least two distinct
  values appear when the database contains 3 or more listings, demonstrating real scoring.
- **SC-003**: A valid search request receives a complete AI-scored response within 35 seconds
  under normal conditions (30-second timeout + 5-second network overhead).
- **SC-004**: When the AI service is unavailable, the endpoint returns an HTTP 503 error within
  the configured timeout period — no hanging requests.
- **SC-005**: A developer can test the AI integration end-to-end in a local environment using
  only the existing search endpoint and a valid AI API key.

## Clarifications

### Session 2026-03-18

- Q: How should the AI return structured match results — JSON output, free-text parsing, or per-listing calls? → A: AI is instructed to return a structured JSON array directly; the backend parses it without regex or pattern matching.
- Q: On transient AI failure (network error, invalid JSON response), should the system retry before returning 503? → A: No retry — any failure immediately returns HTTP 503.
- Q: What language should the AI-generated `reason` strings use? → A: Always Korean, regardless of the language of the user's tags or query.

## Assumptions

- The AI service used is Claude (Anthropic API). The API key is provided via environment
  variable and is not committed to source control.
- For this iteration, all job listings are passed to the AI in a single prompt (batch scoring).
  Individual per-listing AI calls are out of scope due to latency concerns.
- The number of listings passed to the AI is bounded by the existing 10-result limit, so
  prompt size is not expected to exceed model context limits in this iteration.
- Vector embedding and similarity search (planned for a future iteration) are explicitly out
  of scope. This spec covers only direct prompt-based AI scoring.
- No changes to the database schema or job ingestion pipeline are required for this feature.
- The response contract from spec 001 is fully preserved — no new fields are added to the
  API response in this iteration.
- Authentication remains out of scope (inherited from spec 001 assumption).
