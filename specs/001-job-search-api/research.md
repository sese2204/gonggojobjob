# Research: Job Search API

**Branch**: `001-job-search-api` | **Date**: 2026-03-18

## Summary

No external research required. All technical decisions are determined by the existing codebase
and the constitution. This document records the key decisions made during planning.

---

## Decision 1: No filtering at search time

**Decision**: `POST /api/jobs/search` returns the 10 most recently collected listings from the
internal DB without filtering by tags or query content.

**Rationale**: Match scoring and semantic filtering are deferred to AI integration (next phase).
Building filtering now would require replacement when AI is added. Returning recents is the
simplest valid implementation that satisfies the API contract.

**Alternatives considered**:
- Tag keyword LIKE search: rejected — would need to be replaced by AI anyway.
- Return all results: rejected — spec requires exactly 10.

---

## Decision 2: match field as placeholder

**Decision**: `match` field is always returned as `0` for this iteration.

**Rationale**: The API contract requires the field to be present (for frontend contract stability).
The actual value is meaningless until AI integration. `0` is unambiguous as a placeholder.

**Alternatives considered**:
- Random value: rejected — unpredictable, confusing.
- Omit field: rejected — breaks the agreed response contract.

---

## Decision 3: reason field via rule-based template

**Decision**: `reason` is generated server-side from the request's `tags` and `query`:
- Tags present: `"선택하신 {tags} 키워드와 관련된 최신 공고입니다."`
- Query only: `"\"{query}\" 관련 최신 공고입니다."`
- Both: `"선택하신 {tags} 키워드와 \"{query}\" 조건에 맞는 최신 공고입니다."`

**Rationale**: Provides a non-empty, human-readable string that reflects the user's input
without requiring AI. Template is visually coherent with the design mockup in the spec.

---

## Decision 4: CORS via WebMvcConfigurer bean

**Decision**: Add `global/config/CorsConfig.kt` implementing `WebMvcConfigurer`.
Allow origin `http://localhost:5173`, methods `GET, POST`, headers `*`.

**Rationale**: Centralised config is cleaner than per-controller `@CrossOrigin`.
`global/config/` extends the existing `global/` namespace without adding a new top-level
package (no constitution amendment needed).

---

## Decision 5: totalCount and newTodayCount from DB

**Decision**:
- `totalCount`: `jobRepository.count()` — total rows in the jobs table.
- `newTodayCount`: `jobRepository.countByCollectedAtAfter(LocalDate.now().atStartOfDay())`.

**Rationale**: Both values are cheap queries on the single `jobs` table. No caching needed
at this scale.
