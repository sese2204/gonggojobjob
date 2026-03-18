# kotlin-ai Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-03-18

## Active Technologies

- Kotlin 1.9.25 / JVM 21 (existing) + Spring Boot 3.x, Spring Data JPA, springdoc-openapi (existing) (001-job-search-api)

## Project Structure

```text
src/main/kotlin/org/example/kotlinai/
├── controller/     ← @RestController (HTTP only, no business logic)
├── service/        ← @Service + toResponse() extension fns
├── repository/     ← Spring Data JPA interfaces
├── entity/         ← @Entity classes
├── dto/request/    ← inbound data classes
├── dto/response/   ← outbound data classes
└── global/exception/ ← GlobalExceptionHandler, ErrorResponse
src/test/kotlin/    ← mirrors main package structure
backend/
frontend/
tests/
```

## Commands

```bash
./gradlew bootRun --args='--spring.profiles.active=local'   # run locally with API key
./gradlew test                                               # run tests
./gradlew build                                              # build JAR
```

## Code Style

- 4-layer architecture: Controller → Service → Repository → Entity (enforced by constitution)
- `require()` for validation, `orElseThrow {}` for Optional unwrapping
- `@Transactional(readOnly = true)` at class level, override `@Transactional` on write methods
- All exceptions mapped in `GlobalExceptionHandler` (no inline try/catch in services/controllers)
- Expression bodies for single-expression functions

## Recent Changes

- 002-ai-job-integration: Added Kotlin 1.9.25 on JVM 21 + Spring Boot 3.5.11, Spring Data JPA, H2 (dev), `RestClient` (Spring Web), Jackson

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
