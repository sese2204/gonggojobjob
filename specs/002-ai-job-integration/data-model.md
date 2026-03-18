# Data Model: AI Job Matching Integration

**Branch**: `002-ai-job-integration` | **Date**: 2026-03-18

---

## Entities

### JobListing (new — `entity/JobListing.kt`)

Represents a single job posting stored in the internal database.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `Long` | No | Auto-generated primary key |
| `title` | `String` | No | Job title |
| `company` | `String` | No | Company name |
| `url` | `String` | No | Link to the original posting |
| `description` | `String` | Yes | Job description text; passed to AI if present |
| `collectedAt` | `LocalDateTime` | No | Timestamp of when the listing was collected |

**Constraints**:
- `title`, `company`, `url` are mandatory — `require()` validation in service layer.
- `collectedAt` defaults to `LocalDateTime.now()` at entity creation.
- Sort order for retrieval: `collectedAt` descending (most recent first).

---

## DTOs

### `JobSearchRequest` (`dto/request/`)

Inbound search parameters. At least one of `tags` or `query` must be non-empty.

```kotlin
data class JobSearchRequest(
    val tags: List<String> = emptyList(),
    val query: String = ""
)
```

**Validation**: Both `tags` empty AND `query` blank → throw `IllegalArgumentException` (maps to HTTP 400).

---

### `JobResult` (`dto/response/`)

A single job entry in the search response. All fields are AI-populated for this iteration.

```kotlin
data class JobResult(
    val id: String,
    val title: String,
    val company: String,
    val match: Int,      // 0–100, clamped
    val reason: String,  // Korean, AI-generated
    val url: String
)
```

---

### `JobSearchResponse` (`dto/response/`)

Full response from the search endpoint.

```kotlin
data class JobSearchResponse(
    val jobs: List<JobResult>,
    val totalCount: Int,
    val newTodayCount: Int
)
```

---

## Internal AI DTOs (not exposed to API consumers)

### `AiMatchRequest`

Payload assembled by `JobSearchService` before calling `GeminiService`.

```kotlin
data class AiMatchRequest(
    val tags: List<String>,
    val query: String,
    val listings: List<AiJobSummary>
)

data class AiJobSummary(
    val id: String,
    val title: String,
    val company: String,
    val description: String?
)
```

### `AiMatchResult`

Per-listing result parsed from Gemini's JSON response.

```kotlin
data class AiMatchResult(
    val id: String,
    val match: Int,
    val reason: String
)
```

---

## Exceptions

### `AiServiceException` (`global/exception/`)

Thrown by `GeminiService` on any AI call failure (network error, timeout, invalid JSON, empty response).
Mapped to **HTTP 503** in `GlobalExceptionHandler`.

```kotlin
class AiServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

---

## Configuration Properties

Added to `application.yml`:

```yaml
gemini:
  api:
    key: ${GEMINI_API_KEY}
    url: https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent
    timeout-seconds: 30
```

`application-local.yml` (gitignored, local only):

```yaml
gemini:
  api:
    key: <your-actual-api-key>
```
