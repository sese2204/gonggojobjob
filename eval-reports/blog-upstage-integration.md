# Upstage API 사용 후기 — Solar 임베딩 + Groundedness Check 시도기

## 배경

기존 Gemini 임베딩(`gemini-embedding-001`, 768차원)의 검색 품질을 비교 평가하기 위해
Upstage Solar 임베딩(`solar-embedding-1-large`, 4096차원)을 도입했다.
동시에 검색 결과 라벨링 보조 도구로 Groundedness Check API도 시도했다.

---

## Solar 임베딩

### 통합

OpenAI 호환 API라 연동이 간단했다.

```
POST https://api.upstage.ai/v1/embeddings
Authorization: Bearer {API_KEY}

{
  "model": "solar-embedding-1-large-passage",
  "input": ["문서 텍스트"]
}
```

쿼리/문서 비대칭 임베딩을 지원한다.
- 쿼리 임베딩: `solar-embedding-1-large-query`
- 문서 임베딩: `solar-embedding-1-large-passage`

Gemini의 `RETRIEVAL_QUERY` / `RETRIEVAL_DOCUMENT` 태스크타입과 동일한 개념.

### 비용

$0.10 / 1M 토큰. 공고 2,000개 전체 백필 비용 ≈ $0.06.

### 결과

백필 및 벡터 검색 정상 동작. `RAG_EMBEDDING_PROVIDER=UPSTAGE` 환경변수로
Gemini ↔ Upstage 검색 프로바이더 전환 가능.

---

## Groundedness Check — 실패

### 시도 목적

검색 결과 라벨링 시 Gemini(0/1/2 관련성 점수) 외에
Upstage Groundedness Check를 보조 스코어러로 붙이려 했다.

공고 내용(context) + 쿼리 의도 문장(answer) 조합으로
"이 공고가 이 검색어와 관련 있는가"를 판단하는 용도.

### 시도 과정

**1차 시도**: `/v1/groundedness-check` 엔드포인트

```
POST https://api.upstage.ai/v1/groundedness-check
→ 404 Not Found
```

**2차 시도**: Chat Completions 엔드포인트 (`/v1/chat/completions`)

```json
{
  "model": "groundedness-check",
  "messages": [
    {"role": "user",      "content": "{context}"},
    {"role": "assistant", "content": "{answer}"}
  ]
}
→ 400 Bad Request: invalid_request_body
```

**3차 시도**: 모델명 변경 (`groundedness-check-240502`)

```
→ 400 Bad Request: invalid_request_body (동일)
```

**웹 UI 시도**: Upstage 콘솔 웹에서도 사용 불가.

### 원인

콘솔 모델 목록의 `Available platforms: Upstage Console` 항목이
API 제공이 아님을 의미했다. 실질적으로 API로는 접근이 안 되는 베타 기능.

무료 크레딧($10) 플랜에서는 Groundedness Check가 제공되지 않는 것으로 추정.

### 결론

Groundedness Check는 현재 API로 사용 불가. 라벨링은 Gemini 단독으로 진행.
Solar 임베딩 자체는 정상 동작하므로 임베딩 성능 비교 eval은 계속 진행.

---

## 요약

| 기능 | 결과 |
|------|------|
| Solar 임베딩 (`solar-embedding-1-large`) | ✅ 정상 동작 |
| Groundedness Check | ❌ API 미지원 (베타, 콘솔 전용) |

---

*2026-04-22 작성*
