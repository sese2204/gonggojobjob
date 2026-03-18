# API Contract: Job Search

**Endpoint**: `POST /api/jobs/search`
**Version**: 1.0 (002-ai-job-integration — AI scoring activated)

---

## Request

```http
POST /api/jobs/search
Content-Type: application/json
```

```json
{
  "tags": ["React", "Node.js"],
  "query": "프론트엔드 개발자"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `tags` | `string[]` | No* | Skill/keyword tags |
| `query` | `string` | No* | Free-text role description |

*At least one of `tags` (non-empty) or `query` (non-blank) must be provided.

---

## Response: 200 OK

```json
{
  "jobs": [
    {
      "id": "1",
      "title": "프론트엔드 개발자",
      "company": "ABC Corp",
      "match": 92,
      "reason": "React와 Node.js 기술 스택이 공고 요구사항과 정확히 일치하며, 프론트엔드 개발 경험을 필요로 합니다.",
      "url": "https://example.com/jobs/1"
    }
  ],
  "totalCount": 5,
  "newTodayCount": 2
}
```

| Field | Type | Notes |
|---|---|---|
| `jobs` | `JobResult[]` | Sorted by `match` descending, max 10 items |
| `totalCount` | `int` | Total listings in DB |
| `newTodayCount` | `int` | Listings collected today |

### `JobResult` fields

| Field | Type | Notes |
|---|---|---|
| `id` | `string` | Listing identifier |
| `title` | `string` | Job title |
| `company` | `string` | Company name |
| `match` | `int` | AI-generated score 0–100 |
| `reason` | `string` | AI-generated explanation in Korean |
| `url` | `string` | Link to original posting |

---

## Error Responses

### 400 Bad Request — both `tags` and `query` empty

```json
{
  "status": 400,
  "message": "tags 또는 query 중 하나는 반드시 입력해야 합니다."
}
```

### 503 Service Unavailable — AI service failure

```json
{
  "status": 503,
  "message": "AI 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요."
}
```

---

## Gemini API Contract (internal)

`POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={GEMINI_API_KEY}`

### Request body sent to Gemini

```json
{
  "contents": [
    {
      "parts": [
        {
          "text": "당신은 채용 공고 매칭 전문가입니다. 아래 사용자 검색 조건과 공고 목록을 보고, 각 공고에 대해 매칭 점수(0-100)와 한국어로 된 매칭 이유를 평가해주세요.\n\n사용자 조건:\n- 기술 태그: [\"React\", \"Node.js\"]\n- 검색어: 프론트엔드 개발자\n\n공고 목록:\n[{\"id\":\"1\",\"title\":\"...\",\"company\":\"...\",\"description\":\"...\"}]\n\n반드시 아래 형식의 JSON 배열만 반환하세요 (다른 텍스트 없이):\n[{\"id\":\"공고id\",\"match\":점수,\"reason\":\"한국어 이유\"}]"
        }
      ]
    }
  ],
  "generationConfig": {
    "responseMimeType": "application/json"
  }
}
```

### Expected Gemini response (relevant fields)

```json
{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "[{\"id\":\"1\",\"match\":92,\"reason\":\"React와 Node.js 기술 스택이...\"}]"
          }
        ]
      }
    }
  ]
}
```

The `text` field contains the JSON array string to be parsed as `List<AiMatchResult>`.
