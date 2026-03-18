# Feature Specification: Job Search API

**Feature Branch**: `001-job-search-api`
**Created**: 2026-03-18
**Status**: Draft
**Input**: User description: "POST /api/jobs/search — accept tags array + natural language query, return matched job listings with match score + reason + url, sorted by match desc, default 10 results, CORS allow localhost:5173"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Search Jobs by Tags and Query (Priority: P1)

A job seeker on the frontend selects skill tags (e.g., React, Node.js, AWS) and optionally types
a natural language description of the role they are looking for. The frontend sends both to the
backend, which returns a ranked list of matching job listings with a match score and a
human-readable explanation of why each listing was selected.

**Why this priority**: This is the core value proposition of the product. Without this, no other
feature can be demonstrated. It is the only user-facing interaction defined in this spec.

**Independent Test**: Can be fully tested by sending a POST request with tags and query and
verifying that the response contains a sorted list of 10 jobs with match scores, reasons, and URLs.

**Acceptance Scenarios**:

1. **Given** a user has selected tags ["React", "Node.js"] and typed a query,
   **When** the frontend sends POST /api/jobs/search,
   **Then** the response contains a `jobs` array of up to 10 items sorted by `match` descending,
   each item having `id`, `title`, `company`, `match`, `reason`, and `url`.

2. **Given** a user sends only tags with no query string (empty or omitted),
   **When** the backend processes the request,
   **Then** the response still returns a valid `jobs` array (tags-only search is supported).

3. **Given** a user sends only a query string with an empty tags array,
   **When** the backend processes the request,
   **Then** the response returns matching jobs based on the natural language query alone.

4. **Given** no jobs match the search criteria,
   **When** the backend processes the request,
   **Then** the response returns `{ "jobs": [], "totalCount": 0, "newTodayCount": 0 }` with HTTP 200.

5. **Given** the frontend is running on http://localhost:5173,
   **When** it sends POST /api/jobs/search to http://localhost:8080,
   **Then** the response includes CORS headers that allow the browser request to succeed without error.

---

### Edge Cases

- What happens when both `tags` and `query` are absent or empty? System MUST return HTTP 400
  with a descriptive error message (no silent empty result).
- What happens when `tags` contains an unrecognized tag? System MUST treat it as a valid search
  term and include it in matching without returning an error.
- What happens when the internal database is unavailable? System MUST return HTTP 503 rather
  than silently returning an empty list. External API unavailability does not affect search
  responses as external APIs are used only for background data ingestion.
- What happens when two jobs have the same `match` score? System MUST return them in a stable
  order (e.g., newer listings first) to avoid random ordering across calls.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST expose POST /api/jobs/search accepting a JSON body with `tags`
  (array of strings) and `query` (string).
- **FR-002**: System MUST return a JSON response with `jobs` (array), `totalCount` (integer),
  and `newTodayCount` (integer).
- **FR-003**: Each element in `jobs` MUST contain: `id` (string), `title` (string),
  `company` (string), `match` (integer 0–100), `reason` (string), and `url` (string).
  The `reason` field MUST be generated server-side using a rule-based template that
  identifies which of the request's tags/keywords matched the listing
  (e.g., "선택하신 {matched_tags} 키워드와 직무가 일치합니다"). It MUST NOT be empty.
- **FR-004**: The `jobs` array MUST be ordered by `match` score descending. For this iteration,
  match scoring and semantic matching logic are deferred to AI integration; `match` values are
  placeholders and sort order is implementation-defined. The field MUST still be present and
  numeric (0–100) to maintain API contract stability for when AI is integrated.
- **FR-005**: System MUST return at most 10 job listings per response (fixed; no pagination
  parameter in this iteration).
- **FR-006**: System MUST allow cross-origin requests from http://localhost:5173
  (CORS configuration required for development).
- **FR-007**: System MUST return HTTP 400 when both `tags` and `query` are absent or empty.
- **FR-008**: System MUST return HTTP 200 with an empty `jobs` array (not an error) when the
  internal database contains no listings.
- **FR-010**: For this iteration, the backend MUST return the 10 most recently collected
  listings from the internal database without filtering by tags or query content.
  Tag/query-based filtering is deferred to the AI integration phase.
- **FR-009**: The search endpoint MUST always return results exclusively from the internal
  database. External job board APIs (e.g., 사람인, 잡코리아) are used only for data ingestion
  (background crawling/collection) and MUST NOT be called at search request time. AI-powered
  ranking is out of scope for this iteration.

### Key Entities

- **JobSearchRequest**: Inbound search parameters — `tags` (list of skill keywords), `query`
  (free-text description of the desired role).
- **JobListing**: A job posting stored in the internal database. Minimum stored fields:
  `id` (auto-generated), `title`, `company`, `url`, `collectedAt` (timestamp of when the
  listing was collected from an external source). Additional fields such as description,
  tags, and salary are deferred to the AI integration phase.
  API response shape: `id`, `title`, `company`, `match` (placeholder integer 0–100),
  `reason` (rule-based template string), `url`.
- **JobSearchResponse**: The full response — ordered `jobs` list, `totalCount` (total matched
  listings across all pages, not just the returned 10), `newTodayCount` (listings added today).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A frontend developer can call the endpoint from http://localhost:5173 without
  browser CORS errors in a local development setup.
- **SC-002**: A valid search request receives a response within 3 seconds under normal load.
- **SC-003**: Every returned job listing includes a non-empty `reason` field written in plain
  language explaining why it matched the search.
- **SC-004**: The `jobs` array is always in descending `match` order — no out-of-order results
  are ever returned.
- **SC-005**: An invalid request (no tags, no query) receives an error response with sufficient
  detail for the frontend to display a meaningful message to the user.

## Clarifications

### Session 2026-03-18

- Q: When the external job board API fails, what should the search endpoint return? → A: External APIs are used only for background data ingestion (crawling). The search endpoint always returns results from the internal DB exclusively and never calls external APIs at request time.
- Q: How is the `reason` field text generated (AI is out of scope)? → A: Rule-based template combining matched tags/keywords (e.g., "선택하신 {matched_tags} 키워드와 직무가 일치합니다").
- Q: How is the `match` score (0–100) calculated? → A: Match scoring and semantic matching logic are deferred to AI integration. For this iteration, `match` is a placeholder value and job ranking order is implementation-defined.
- Q: How does the backend select which jobs to return from the internal DB? → A: No filtering applied. Return the 10 most recently collected listings regardless of tags or query. Filtering/matching deferred to AI integration phase.
- Q: What fields does the Job entity store in the internal DB? → A: Minimum fields only — `title`, `company`, `url`, `collectedAt` (collection timestamp) plus response fields. Additional fields (description, tags, etc.) deferred to AI integration phase.

## Assumptions

- Authentication is out of scope; the endpoint is publicly accessible. JWT will be added later.
- Pagination is out of scope for this iteration. A future `page`/`size` parameter will be added
  without breaking the current response contract.
- `totalCount` represents the total number of matched listings in the data source, not just the
  10 returned items. `newTodayCount` reflects listings added today across the full data source.
- The `match` field is an integer (0–100) and MUST be present in every job object to maintain
  API contract stability. Actual match scoring and semantic ranking are deferred to the AI
  integration phase; placeholder values are acceptable for this iteration.
- Job `id` values are string identifiers (e.g., "job_001") to accommodate external source IDs.
- The CORS allowance for http://localhost:5173 applies to the development environment only.
  Production CORS policy will be defined separately.
