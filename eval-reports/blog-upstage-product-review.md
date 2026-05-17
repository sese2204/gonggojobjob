# Upstage API 사용 후기 — Solar 임베딩으로 검색 품질 A/B 테스트

## 배경

한국어 대외활동 공고 검색 서비스에 RAG 하이브리드 검색(pgvector + pg_trgm)을 붙여두고 있었다.
기존에는 Gemini `gemini-embedding-001` (768차원)으로 벡터 임베딩을 생성했는데,
Upstage의 Solar 임베딩이 한국어 특화 + 4096차원이라는 점이 흥미로워서 A/B 비교를 해봤다.

---

## 통합 과정

### Solar 임베딩

OpenAI 호환 API라 연동이 빠르다.

```
POST https://api.upstage.ai/v1/embeddings
Authorization: Bearer {API_KEY}
Content-Type: application/json

{
  "model": "solar-embedding-1-large-passage",
  "input": ["문서 텍스트"]
}
```

쿼리/문서 비대칭 임베딩을 지원하는 것이 Gemini와 같은 개념이라 자연스럽게 매핑됐다.

| 용도 | Gemini | Upstage Solar |
|------|--------|---------------|
| 쿼리 임베딩 | `RETRIEVAL_QUERY` | `solar-embedding-1-large-query` |
| 문서 임베딩 | `RETRIEVAL_DOCUMENT` | `solar-embedding-1-large-passage` |

비용: $0.10 / 1M 토큰. 공고 약 2,000개 전체 백필 비용 ≈ $0.06.

pgvector에 `vector(4096)` 컬럼을 추가하고 백필 후, `RAG_EMBEDDING_PROVIDER=UPSTAGE` 환경변수로
Gemini ↔ Upstage 전환이 가능하도록 구성했다.

### Groundedness Check — 실패

라벨링 보조 도구로 Groundedness Check도 써보려 했는데 API 접근이 안 됐다.

- `/v1/groundedness-check` → 404
- `/v1/chat/completions` + `model: groundedness-check` → 400
- `/v1/chat/completions` + `model: groundedness-check-240502` → 400
- 웹 콘솔 UI에서도 불가

콘솔 모델 목록에 `Available platforms: Upstage Console`로 표시된 것이
API가 아닌 웹 전용 베타 기능임을 나중에 파악했다. 라벨링은 Gemini로만 진행.

---

## A/B 실험 설계

### 평가 방법론

TREC 스타일 풀링 방식:
1. 쿼리당 후보 풀 생성 (Gemini hybrid + Upstage vector + ILIKE, 최대 80개)
2. Gemini LLM이 각 후보에 0/1/2 관련성 점수 부여
3. score=2만 relevantIds로 확정
4. Recall@10, nDCG@10, P@5, MRR 측정

30개 쿼리 세트: head(단일 토큰), short_korean(2~3 토큰), long_natural(자연어), synonym(유의어), adversarial(오타/변형).

### 1차 실험의 실수 — 라벨 편향

처음에는 라벨링 후보 풀을 Gemini 검색 결과로만 구성했다.
당연히 Upstage가 찾는 문서는 후보에 없어서 relevantIds에도 없고, 정답으로 인정받지 못했다.

```
Round 2 결과 (편향된 라벨):
  Gemini: Recall@10 = 0.834
  Upstage: Recall@10 = 0.737  ← 불공정한 비교
  격차: 0.097
```

2차에서 후보 풀에 Upstage 결과도 합산 후 재라벨링·재실험했다.

---

## 최종 결과 (Round 3 — 공정한 라벨)

| 지표 | Gemini 768d | Upstage Solar 4096d |
|------|------------|---------------------|
| Recall@10 | **0.752** | 0.711 |
| nDCG@10 | **0.954** | 0.906 |
| P@5 | **0.792** | 0.758 |
| MRR | **1.000** | 1.000 |

라벨 편향 수정 후 격차가 0.097 → 0.041로 절반 감소.
하지만 여전히 Gemini 임베딩이 전반적으로 우위다.

### 카테고리별 반전

| Category | Gemini | Upstage | 승자 |
|---|---|---|---|
| head (단일 토큰) | 0.559 | **0.612** | **Upstage** |
| short_korean | **0.764** | 0.650 | Gemini |
| long_natural | **0.722** | 0.681 | Gemini |
| synonym | **0.897** | 0.821 | Gemini |
| adversarial | **1.000** | 1.000 | 동률 |

"인턴", "공모전" 같은 단일 토큰 쿼리에서만 Upstage가 앞선다.
짧은 한국어에서 한국어 특화 임베딩의 강점이 살짝 보이는 수준.

---

## 소감

**기대와 달랐던 것**:
한국어 특화 + 4096차원이면 당연히 한국어 도메인에서 이길 줄 알았는데,
전반적으로는 Gemini가 앞섰다. 차원 수가 많다고 무조건 좋은 게 아니라는 것.
도메인(대외활동 공고 특유의 구조화된 텍스트)과 임베딩 모델의 궁합이 더 중요한 것 같다.

**Upstage의 강점**:
- OpenAI 호환 API — 연동 비용이 거의 없다
- 비용이 저렴하다 ($0.10/1M)
- 단일 토큰 한국어 쿼리에서 미세하게 우위
- 비대칭 임베딩(query/passage) 지원

**아쉬운 점**:
- Groundedness Check가 API로 접근 불가 (베타, 콘솔 전용)
- 4096차원 벡터는 저장 공간과 인덱스 크기 부담이 크다 (768d의 약 5.3배)
- head 쿼리 외에는 Gemini 대비 뚜렷한 우위가 없음

**결론**:
현재 서비스에서는 Gemini 임베딩을 유지하기로 했다.
Upstage Solar는 짧은 한국어 키워드 중심의 도메인이거나, 비용 절감이 중요한 경우에
Gemini 대안으로 고려할 만하다.

---

*2026-04-22 작성*
