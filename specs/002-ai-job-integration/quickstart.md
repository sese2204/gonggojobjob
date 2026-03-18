# Quickstart: AI Job Matching Integration

**Branch**: `002-ai-job-integration`

---

## Prerequisites

- JDK 21
- A Google Gemini API key ([Google AI Studio](https://aistudio.google.com/))

---

## Local Setup

1. **Create `application-local.yml`** (never commit this file):

   ```yaml
   # src/main/resources/application-local.yml
   gemini:
     api:
       key: YOUR_GEMINI_API_KEY_HERE
   ```

2. **Verify `.gitignore`** includes `application-local.yml` (added as part of this feature).

3. **Run the application with the `local` profile**:

   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

   Or in IntelliJ: Edit Run Configuration → Active profiles → `local`

4. **Test the endpoint**:

   ```bash
   curl -X POST http://localhost:8080/api/jobs/search \
     -H "Content-Type: application/json" \
     -d '{"tags": ["React", "Node.js"], "query": "프론트엔드 개발자"}'
   ```

   The server seeds 5 sample job listings on startup. You should receive a response with
   AI-generated `match` scores and Korean `reason` strings.

---

## GitHub Actions Setup

1. Go to your GitHub repository → **Settings** → **Secrets and variables** → **Actions**
2. Click **New repository secret**
3. Name: `GEMINI_API_KEY`, Value: your API key
4. In your workflow YAML, expose it as an environment variable:

   ```yaml
   - name: Build and test
     env:
       GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
     run: ./gradlew test
   ```

---

## Verifying AI Integration

A successful response looks like:

```json
{
  "jobs": [
    {
      "id": "1",
      "title": "프론트엔드 개발자",
      "company": "스타트업 A",
      "match": 88,
      "reason": "React 프레임워크와 Node.js 백엔드 경험을 요구하며, 검색하신 기술 스택과 높은 연관성을 보입니다.",
      "url": "https://example.com/jobs/1"
    }
  ],
  "totalCount": 5,
  "newTodayCount": 0
}
```

**Validation checks**:
- `match` values are not all identical across listings
- `reason` strings are in Korean and reference the query/tags
- Response arrives within 35 seconds

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| HTTP 503 on every request | `GEMINI_API_KEY` not set or invalid | Check `application-local.yml` or env var |
| `IllegalStateException` on startup | `GEMINI_API_KEY` env var missing (non-local profile) | Set the env var or use `local` profile |
| All `match` scores identical | AI returned malformed JSON → fallback triggered | Check application logs for parse errors |
