# Quickstart: Job Search API

**Branch**: `001-job-search-api` | **Date**: 2026-03-18

## Prerequisites

- JDK 21
- Gradle (or use `./gradlew`)
- App running on `http://localhost:8080`

## 1. Start the application

```bash
./gradlew bootRun
```

## 2. Search for jobs

```bash
curl -X POST http://localhost:8080/api/jobs/search \
  -H "Content-Type: application/json" \
  -d '{
    "tags": ["React", "Node.js"],
    "query": "대용량 트래픽 서버 개발자"
  }'
```

**Expected response**:

```json
{
  "jobs": [
    {
      "id": "1",
      "title": "백엔드 개발자",
      "company": "스타트업 A",
      "match": 0,
      "reason": "선택하신 React, Node.js 키워드와 \"대용량 트래픽 서버 개발자\" 조건에 맞는 최신 공고입니다.",
      "url": "https://example.com/job/1"
    }
  ],
  "totalCount": 1,
  "newTodayCount": 1
}
```

## 3. Test empty DB response

If no jobs have been collected yet:

```json
{
  "jobs": [],
  "totalCount": 0,
  "newTodayCount": 0
}
```

## 4. Test validation error

```bash
curl -X POST http://localhost:8080/api/jobs/search \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Expected response (HTTP 400)**:

```json
{
  "status": 400,
  "message": "태그 또는 검색어를 입력해주세요"
}
```

## 5. Verify CORS (from frontend)

From the Vite dev server at `http://localhost:5173`:

```js
const res = await fetch('http://localhost:8080/api/jobs/search', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ tags: ['React'], query: '' }),
});
const data = await res.json();
console.log(data.jobs);
```

Should succeed without CORS errors.

## 6. Swagger UI

Visit `http://localhost:8080/swagger-ui/index.html` to explore the endpoint interactively.

## Adding test data

Since the DB is H2 in-memory and resets on restart, insert test data via the H2 console
at `http://localhost:8080/h2-console` or use a `data.sql` file in `src/main/resources/`.
