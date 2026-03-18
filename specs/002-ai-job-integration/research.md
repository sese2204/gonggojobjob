# Research: AI Job Matching Integration

**Branch**: `002-ai-job-integration` | **Date**: 2026-03-18

---

## Decision 1: AI Provider

**Decision**: Google Gemini (gemini-2.0-flash) via REST API

**Rationale**: User specified Gemini. `gemini-2.0-flash` is the latest efficient model —
low cost, fast, and supports structured JSON output via `responseMimeType: "application/json"`,
which aligns with FR-004 (AI must return parseable JSON without regex).

**Alternatives considered**:
- `gemini-1.5-flash`: Previous gen, still viable; superseded by 2.0-flash.
- Vertex AI SDK: More complex setup (requires GCP project + service account). Overkill for
  a test iteration; plain REST API with an API key is sufficient.
- Google AI Java/Kotlin SDK (`com.google.ai.client.generativeai`): Primarily targets Android/
  Kotlin Multiplatform. Not the idiomatic choice for JVM/Spring Boot — REST call via
  `RestClient` is simpler and has no platform-specific gotchas.

---

## Decision 2: HTTP Client for Gemini API

**Decision**: Spring `RestClient` (introduced in Spring Boot 3.2)

**Rationale**: Already available via `spring-boot-starter-web` — no new dependency needed.
Fluent, synchronous API that fits the blocking request/response model used in the rest of
this project. Timeout is configurable via `java.net.http.HttpClient` or `SimpleClientHttpRequestFactory`.

**Alternatives considered**:
- `WebClient` (reactive): Unnecessary complexity for a synchronous endpoint.
- `RestTemplate` (legacy): Deprecated in favor of `RestClient` in Spring Boot 3.x.
- OkHttp / Retrofit: Additional dependency with no advantage over `RestClient` here.

---

## Decision 3: Gemini API Call Structure

**Decision**: Single batch prompt — all job listings + user criteria in one request.

The prompt instructs Gemini to return a JSON array:
```
[{"id": "...", "match": <int 0-100>, "reason": "<Korean string>"}]
```

`generationConfig.responseMimeType` is set to `"application/json"` to constrain output format.
The `id` field correlates each result back to the original listing.

**Rationale**: Single call minimizes latency and API cost. Bounded by the 10-listing limit,
so prompt size stays well within Gemini's context window.

**Alternatives considered**:
- Per-listing calls: 10× API round-trips, ~10× latency. Rejected (FR-003 explicitly requires batch).
- Streaming responses: Unnecessary complexity; result is small and bounded.

---

## Decision 4: API Key Management

**Decision**: Environment variable `GEMINI_API_KEY`, loaded via Spring's property binding.

| Scenario | Mechanism |
|---|---|
| Local development | `src/main/resources/application-local.yml` (gitignored) |
| GitHub Actions CI | Repository secret `GEMINI_API_KEY` → env var in workflow step |
| Production (future) | Same env var pattern, injected by the hosting platform |

`application.yml` reads `${GEMINI_API_KEY}` so the key never appears in any committed file.
`application-local.yml` must be added to `.gitignore`.

**Alternatives considered**:
- `local.properties` (Gradle style): Works but less idiomatic for Spring Boot; requires custom
  property source loader.
- `.env` file with dotenv library: Extra dependency; Spring profiles solve this natively.
- Hardcoded in `application.yml`: Security violation — never acceptable.

---

## Decision 5: 503 Error Mapping

**Decision**: Introduce a custom `AiServiceException` that the `GlobalExceptionHandler` maps
to HTTP 503.

**Rationale**: Constitution (Principle IV) forbids inline try/catch in services and requires
all exception-to-HTTP mappings to live in `GlobalExceptionHandler`. A dedicated exception type
makes the 503 mapping explicit and testable.

**Alternatives considered**:
- `RuntimeException` with a flag: Loses semantic clarity.
- Returning an error DTO from the service: Violates the exception-based error handling
  convention already established in the codebase.

---

## Decision 6: Test Job Data

**Decision**: Seed a small set of hardcoded `JobListing` rows via a `DataLoader` bean
(`CommandLineRunner`) that runs on startup (dev/test profile only).

**Rationale**: Spec 001 (the job ingestion pipeline) has not been implemented yet. For this
test iteration, 3–5 hardcoded listings are sufficient to validate real AI scoring without
requiring a fully operational ingestion pipeline.

**Alternatives considered**:
- `data.sql` seed file: Works, but `CommandLineRunner` is more idiomatic for entity-managed JPA
  with `create-drop` DDL.
- Wait for spec 001 implementation: Blocks testing; unnecessary dependency for this iteration.
