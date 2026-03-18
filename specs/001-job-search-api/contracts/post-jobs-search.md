# Contract: POST /api/jobs/search

**Branch**: `001-job-search-api` | **Date**: 2026-03-18

## Endpoint

```
POST /api/jobs/search
Content-Type: application/json
```

## Request Body

```json
{
  "tags": ["React", "Node.js", "AWS"],
  "query": "대용량 트래픽 다뤄볼 수 있는 서버 개발자"
}
```

| Field   | Type            | Required | Description                                     |
|---------|-----------------|----------|-------------------------------------------------|
| `tags`  | `List<String>`  | Conditional | Skill/technology keywords. At least one of `tags` or `query` must be non-empty. |
| `query` | `String`        | Conditional | Natural language job description query. At least one of `tags` or `query` must be non-empty. |

**Validation**: If both `tags` is empty/absent AND `query` is blank/absent → HTTP 400.

## Response — 200 OK

```json
{
  "jobs": [
    {
      "id": "1",
      "title": "백엔드 개발자 (Java/Spring)",
      "company": "스타트업 A",
      "match": 0,
      "reason": "선택하신 Node.js, AWS 키워드와 관련된 최신 공고입니다.",
      "url": "https://example.com/job/1"
    }
  ],
  "totalCount": 142853,
  "newTodayCount": 1245
}
```

| Field           | Type             | Description                                                       |
|-----------------|------------------|-------------------------------------------------------------------|
| `jobs`          | `List<JobResponse>` | Up to 10 job listings, sorted by `collectedAt` descending (most recent first). |
| `totalCount`    | `Long`           | Total number of job listings in the internal database.            |
| `newTodayCount` | `Long`           | Number of listings collected today (since midnight).              |

### JobResponse fields

| Field     | Type     | Description                                                                    |
|-----------|----------|--------------------------------------------------------------------------------|
| `id`      | `String` | Job identifier (string representation of internal DB id).                      |
| `title`   | `String` | Job posting title.                                                             |
| `company` | `String` | Company name.                                                                  |
| `match`   | `Int`    | Relevance score 0–100. **Placeholder value `0` in this iteration.**            |
| `reason`  | `String` | Rule-based explanation referencing the user's tags/query. Never empty.         |
| `url`     | `String` | Link to the original job posting.                                              |

## Response — 400 Bad Request

```json
{
  "status": 400,
  "message": "태그 또는 검색어를 입력해주세요"
}
```

Triggered when both `tags` and `query` are absent or empty.

## Response — 200 OK (no results)

```json
{
  "jobs": [],
  "totalCount": 0,
  "newTodayCount": 0
}
```

Returned when the internal database contains no job listings. Not an error.

## CORS

During development, the endpoint allows cross-origin requests from:

```
Origin: http://localhost:5173
Methods: GET, POST
Headers: *
```

## Notes

- `match` is always `0` in this iteration. Will be replaced by AI-computed score in a future phase.
- `jobs` is sorted by collection recency (newest first), not by match score, in this iteration.
- `totalCount` reflects all rows in the jobs table, not a filtered count.
- `newTodayCount` counts listings with `collectedAt >= today 00:00:00`.
