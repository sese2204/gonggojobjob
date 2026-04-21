# 한국어 하이브리드 검색 품질을 수치로 개선하기

> pgvector + Postgres FTS 위에서 돌아가는 학생 대외활동 검색기를 대상으로,
> "감으로 좋아진 것 같다" 를 "숫자로 +4% Recall 올렸다"로 바꾼 이야기.
> Phase 0(평가 하네스 구축) → Phase 1(키워드 쿼리 개선) → Phase 2(HyDE 실험과 기각)
> 까지 전 과정을 기록했다.

## TL;DR

- 30개 쿼리의 **골드 라벨** 을 TREC 방식 pooled relevance + LLM 보조 라벨링으로 만들어 둠.
- **Phase 1** (키워드 쿼리를 AND → OR+prefix, `ts_rank` → `ts_rank_cd`, ILIKE fallback 제거) 로 Recall@10 +4%, nDCG@10 +3%. 짧은 한국어 / 긴 자연어 쿼리에서 특히 개선.
- **Phase 2** (HyDE, 순수 / 앙상블 둘 다) 는 이 코퍼스 + 비대칭 임베딩 모델 조합에서 **역효과** 로 기각. 하지만 "왜 안 먹히는가"를 숫자로 설명할 수 있게 됨.
- **가장 중요한 교훈**: 평가 하네스 없이 검색 개선을 시도하는 건 도박이다. 코퍼스 드리프트(매일 갱신되는 DB)까지 고려한 **동일 코퍼스 비교**가 필요.

---

## 1. 왜 검색 품질을 "측정" 해야 하는가

처음에 내가 받은 피드백은 단순했다.

> "인턴 IT" 치면 죄다 공모전만 뜬다.

개선 아이디어는 바로 떠오른다 — 키워드 쿼리를 AND 에서 OR 로 바꾼다, 임베딩 가중치를 올린다, HyDE를 붙인다… 문제는 **어느 것이 실제로 개선이고 어느 것이 자위인지** 알 방법이 없다는 거다.

- 내가 보기엔 좋아진 것 같아도 다른 쿼리에서 깨질 수 있음 (regression)
- 오늘 좋아진 결과가 내일은 달라질 수 있음 (코퍼스가 매일 바뀜)
- "이게 괜찮은 수치인가" 를 판단할 기준선(baseline) 이 필요

그래서 실제 개선 작업을 시작하기 전에, **재현 가능한 오프라인 평가 하네스** 부터 구축했다. 이게 Phase 0 이다.

---

## 2. Phase 0 — 평가 하네스 설계

### 2.1 IR 평가의 기본 전제

검색은 **분류(classification) 문제가 아니라 랭킹(ranking) 문제** 다.

- 정답이 "하나" 가 아니다. "관련 있는 문서들의 집합" 이다.
- 순위가 품질의 일부다. 같은 문서라도 1위에 있는 것과 10위에 있는 것은 가치가 다르다.
- 따라서 메트릭도 집합 기반(Recall, Precision) 과 랭킹 기반(nDCG, MRR) 을 **같이** 본다.

### 2.2 Pooled Relevance — 전수 라벨링을 피하는 법

DB에 1,000+ 건이 들어있는 상황에서 쿼리 × 문서를 전수로 라벨링하는 건 불가능하다. 그래서 TREC 컨퍼런스에서 쓰는 **pooled relevance** 방식을 채택했다.

1. 쿼리별로 "후보 pool" 을 만든다 — 현재 검색의 top-N + 토큰별 ILIKE 결과 합집합.
2. 이 pool 안에 있는 것만 0/1/2 점으로 라벨링한다.
3. Pool 밖은 "암묵적으로 관련없음" 으로 취급.

**단점**: pool 밖에 있는 진짜 관련 문서는 영영 발굴 못 함. 새 알고리즘이 그걸 찾아내도 "엉뚱한 결과를 찾았다"고 오판될 수 있음.
**완화**: pool 생성을 **여러 검색법의 합집합** 으로 해서 다양성을 확보. 그리고 어차피 같은 조건에서 before/after 를 비교하니 상대 비교는 유효하다.

### 2.3 LLM + 사람 하이브리드 라벨링

1,200+ 쌍을 사람이 전부 채점하는 건 비현실적이다. 그렇다고 LLM만 쓰면 환각 리스크가 있다. 절충:

- **1단계**: Gemini 에게 (쿼리, 후보 공고) 쌍에 대해 rubric(아래)을 주고 0/1/2 점 요청
- **2단계**: 2점 받은 항목 + 애매한 1점은 내가 눈으로 검수, 판단 뒤집을 수 있으면 뒤집음

LLM 은 recall-oriented 스크리너 역할. 최종 판단은 사람. 비용은 라벨링 1 사이클 당 $0.3 수준 (Gemini Flash 기준).

**Rubric**:
- **relevant (2점)**: 카테고리 / 직무 / 주제가 쿼리 의도에 정확히 부합. 사용자가 클릭할 만함.
- **marginal (1점)**: 키워드는 스치지만 주된 의도가 어긋남. 부차적.
- **irrelevant (0점)**: 카테고리가 완전히 다르거나 우연한 키워드 매칭.

**strict threshold**: 최종 `relevantIds` 에는 **2점만** 들어간다. 1점은 `marginalIds` 필드로 따로 둬서 나중에 graded nDCG 로 확장할 여지만 남겼다.

### 2.4 Eval Set — 30개 쿼리, 5개 카테고리

통계적으로는 30개가 적지만, **절대값이 아닌 변화 방향만** 본다면 충분하다. 카테고리를 쪼개서 regression 감지 민감도를 높였다.

| 카테고리 | 개수 | 목적 |
|---|---|---|
| `head` | 6 | "인턴", "공모전" 같은 단일 토큰. Regression 감지 기준선. |
| `short_korean` | 10 | "마케팅 대학생", "AI 공모전" 같은 2~3 토큰 한국어. **현재 깨진 케이스 재현.** |
| `long_natural` | 6 | "백엔드 개발 직무 체험 프로그램" 같은 긴 자연어. HyDE 강점이 기대되는 영역. |
| `synonym` | 4 | "ML", "UX/UI" 같은 약어 / 동의어 / 특수문자. |
| `adversarial` | 4 | "xyz123", "인턴인턴인턴" 같은 오타 / 무의미 / 반복. 이 쿼리들은 `relevantIds` 가 의도적으로 비어있고 zero-result rate 만 본다. |

### 2.5 메트릭 정의

모든 메트릭은 쿼리 1건당 계산 → 전체 평균 + 카테고리별 평균.

- **Recall@10** (K=10): top-10 안에 들어온 관련 문서 수 ÷ 전체 관련 문서 수. "놓치지 않았는가" — 이번 프로젝트의 **가장 중요한 지표**. "인턴 쳤는데 인턴이 안 뜬다" 문제가 정확히 이 지표에 잡힌다.
- **nDCG@10**: 관련 문서가 상위일수록 가산, `log2` 할인으로 하위 rank 페널티. Ideal ordering 대비 비율. "같은 걸 찾았어도 1위냐 10위냐" 를 구분.
- **Precision@5**: top-5 중 관련 문서 비율. "상위에 쓰레기가 얼마나 섞였나" — "공모전이 상위를 먹는 문제" 를 직접 포착.
- **MRR** (Mean Reciprocal Rank): 첫 관련 문서가 나타난 rank 의 역수. 사용자 첫인상.
- **Zero-result rate**: 빈 결과가 나온 쿼리 비율. 키워드 AND 버그의 직접 지표.
- **Latency p50 / p95**: cold-path 응답 시간. 캐시 clear 후 첫 호출만 측정.

### 2.6 구현 — Gradle Test task 로 JSON 리포트

실제 구현은 두 개의 JUnit 태그 테스트로 나눴다.

- `@Tag("eval-label")` `LabelingHelperTest` — pool 생성 + Gemini 채점 + YAML 초안 출력
- `@Tag("eval")` `SearchEvalTest` — `eval-queries.yaml` 로드 → 쿼리 실행 → JSON 리포트 출력

Gradle 쪽:

```kotlin
tasks.register<Test>("evalLabel") {
    useJUnitPlatform { includeTags("eval-label") }
}
tasks.register<Test>("evalTest") {
    useJUnitPlatform { includeTags("eval") }
}
```

JSON 한 예시:

```json
{
  "label": "phase1-2",
  "commit": "6e90632",
  "snapshotDate": "2026-04-20",
  "summary": {
    "recallAt10Mean": 0.826,
    "ndcgAt10Mean": 0.926,
    "precisionAt5Mean": 0.738,
    "mrrMean": 0.940,
    "latencyP50Ms": 18020
  },
  "byCategory": {
    "short_korean": { "recallAt10Mean": 0.880, ... },
    "long_natural": { "recallAt10Mean": 0.718, ... }
  }
}
```

`EVAL_LABEL` 환경변수로 라벨을 주면 파일명이 `<label>-<snapshot>.json` 으로 떨어져서, 같은 코퍼스에서 여러 변종을 줄줄이 돌린 결과를 정리하기 편하다.

### 2.7 함정 — 재현성을 망치는 것들

하네스 구축하면서 세 가지 함정을 피해야 했다.

1. **Spring 캐시**: `SearchCacheService` 가 5분 TTL 로 같은 쿼리 결과를 보관. 두 번째 실행부터는 캐시 히트라 첫 호출 latency 측정이 오염됨 → 쿼리마다 reflection 으로 cache 필드를 clear.
2. **Daily search limit**: 로그인 사용자는 하루 15회 제한이 걸리는데, 30개 쿼리를 연속 돌리면 중간에 끊김 → `SecurityContextHolder.clearContext()` 로 익명 요청처럼 우회 (익명은 limit 미적용).
3. **백그라운드 ingestion 스케줄러**: 한국시간 05:00~08:00 에 매일 돌면서 DB 갱신. eval 중에 데이터가 바뀌면 재현 불가 → `eval` 프로파일에서 `spring.task.scheduling.enabled=false`.

그리고 설계 원칙 하나 더: **Spring 풀 컨텍스트** 로 테스트를 돌린다 (`@SpringBootTest`). Mock으로 좁히면 실제 pgvector 인덱스 / FTS tsquery 실행이 안 돼서, 이 프로젝트에서 필요한 측정 자체가 불가능하다.

---

## 3. Phase 1 — 키워드 쿼리 개선

Phase 0 으로 숫자를 볼 수 있게 됐으니, 본격 개선 시작.

### 3.1 가설과 진단

`short_korean` 카테고리의 baseline Recall@10 이 0.42 수준으로 낮았다. 실제 SQL 을 살펴보니 원인이 바로 보였다.

**Before (baseline 코드)**:

```kotlin
val tsQuery = tokens.joinToString(" & ") // AND join
// ex: "인턴 IT" → "인턴 & IT"
repository.findByKeyword(tsQuery) // ts_rank order, ILIKE fallback
```

세 가지 문제가 있었다.

1. **AND join**: "인턴 IT" → `to_tsquery('인턴 & IT')` → "인턴" *과* "IT" 를 **둘 다** 가진 공고만 매칭. 공고 대부분이 한쪽 토큰만 가지고 있으니 결과가 거의 없음 → vector 쪽에만 의존 → 공모전이 상위 점령.
2. **`ts_rank`**: 단순 term frequency 기반 스코어. 같은 토큰이 여러 번 등장하는 장문 공고가 유리 → 실제로는 다양한 토큰을 고르게 가진 공고가 더 관련성 높을 수 있는데 역행.
3. **ILIKE fallback**: tsquery 결과가 비면 `%token%` 로 다시 훑는 hacky fallback. 정렬 기준이 없어 무작위에 가깝고, 한글 부분 매칭에 오탐이 많음.

### 3.2 수정

```kotlin
private fun fetchKeywordResults(searchText: String, limit: Int): List<ActivityListing> {
    val tokens = searchText.trim()
        .replace("[()/<>&|!:*\\\\]".toRegex(), " ")
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()

    // OR + prefix: "인턴:* | IT:*"
    val tsQuery = tokens.joinToString(" | ") { "$it:*" }
    return activityListingRepository.findByKeyword(tsQuery, limit)
}
```

Repository 쪽:

```sql
-- ORDER BY ts_rank(...) → ts_rank_cd(...)
ORDER BY ts_rank_cd(to_tsvector('simple', ...), to_tsquery('simple', :tsquery)) DESC
```

세 가지 변화.

1. **OR + prefix 매칭** (`token:* | token:*`): 2~3 토큰 쿼리가 모든 토큰을 요구하지 않도록. prefix 덕분에 "인턴" 검색이 "인턴십" / "인턴쉽" 같은 형태도 매칭.
2. **`ts_rank_cd` (cover density)**: term frequency 만 보는 게 아니라 토큰들의 **근접도(proximity)** 도 반영. "IT 인턴" 이라는 구가 연달아 나오는 문서가 토큰 따로따로 나오는 문서보다 상위.
3. **ILIKE fallback 제거**: RRF 가 vector 와 keyword 양쪽을 합쳐주니 키워드가 빈 결과를 내도 vector 에서 보완됨. fallback 불필요.

### 3.3 첫 측정 — 그런데 regression?

```
baseline (04-18):  Recall@10 = 0.52
phase1   (04-20):  Recall@10 = 0.48   ← 감소
```

당황스러웠다. 카테고리별로 보니 `head` 가 유독 떨어졌다. 코드를 다시 훑어봐도 regression 일 이유가 없었다.

### 3.4 함정 — Corpus Drift

원인은 **DB 가 바뀌어 있었다**. 이틀 사이에 ingestion 스케줄러가 3번 돌면서:

- 마감된 공고가 삭제됨 → `relevantIds` 의 일부 id 가 DB에 없음 → 검색이 찾을 수 없음 → Recall 하락
- 새 공고가 들어옴 → 상위에 올라오지만 라벨링이 안 됨 → "관련 없음" 취급 → Precision 하락

즉 알고리즘이 나빠진 게 아니라 **채점 기준지 자체가 움직였다**. 라벨은 04-18 스냅샷 기준인데 코드는 04-20 코퍼스로 돌린 셈.

**대응**:

1. 04-20 코퍼스로 후보 pool 다시 만들고, Gemini 재채점 + 수검수 → `eval-queries.yaml` v2 생성.
2. **동일 코퍼스에서 baseline 코드도 다시 실행** → `baseline-2` 라벨로 JSON 별도 저장.
3. 진짜 비교는 `baseline-2` vs `phase1-2` 여야 함.

### 3.5 정직한 결과

```
baseline-2 → phase1-2 (동일 04-20 코퍼스, v2 라벨)
Recall@10:  0.794 → 0.826   (+4.0%)
nDCG@10:    0.897 → 0.926   (+3.2%)
P@5:        0.715 → 0.738   (+3.2%)
MRR:        0.939 → 0.940   (flat)
p50:        17.2s → 18.0s   (+0.8s, 허용 범위)
```

카테고리별 Recall@10:

| 카테고리 | baseline-2 | phase1-2 | Δ |
|---|---|---|---|
| head (n=6) | 0.687 | 0.694 | +0.7pp |
| **short_korean (n=10)** | 0.819 | **0.880** | **+6.1pp** |
| **long_natural (n=6)** | 0.668 | **0.718** | **+5.0pp** |
| synonym (n=4) | 0.939 | 0.939 | 0 |
| adversarial (n=4) | 1.000 | 1.000 | 0 |

- 목표했던 `short_korean` 과 `long_natural` 에서 확실한 개선.
- `head` 는 거의 flat — 단일 토큰은 원래도 키워드가 잘 먹었고, 추가로 개선할 여지가 없었음.
- `synonym` / `adversarial` 은 천장/바닥에 붙어있어서 변화 없음.

### 3.6 교훈

- **매일 갱신되는 DB 에서는 알고리즘 변경 과 **반드시** 같은 날 baseline 재측정이 한 세트** 여야 한다. 아니면 코퍼스 드리프트가 전부 알고리즘 효과처럼 보인다.
- 라벨도 함께 버전 관리. `snapshotDate` 필드를 JSON 에 박아서 어떤 코퍼스에서 측정된 건지 추적 가능하게.

---

## 4. 옆 길 — RRF 가중치 튜닝

Phase 1 이 끝나고 다음 stride 전에 한 가지 의문이 있었다.

> RRF 가중치를 `0.5 / 0.5` 말고 다른 값으로 바꾸면 어떻게 될까?

### 4.1 RRF 가 뭐였더라

하이브리드 검색에서 벡터 결과 리스트와 키워드 결과 리스트를 어떻게 합칠까? 가장 단순한 방법이 **Reciprocal Rank Fusion**.

```kotlin
score(doc) = vectorWeight  * 1 / (60 + vecRank)
           + keywordWeight * 1 / (60 + kwRank)
```

- `k = 60` 은 업계 관습값 (상위 rank 가 혼자 지배하지 않게 completeness 확보).
- 두 리스트에 모두 등장한 문서는 두 점수 합산, 한쪽에만 있으면 해당 쪽 점수만.
- 최종 점수 내림차순 정렬 → 상위 N 반환.

점수를 직접 정규화하는 것보다 **rank 만** 사용하므로 각 시스템의 스코어 분포에 무감하고, 구현이 단순한 게 강점.

### 4.2 실험 — 왜 튜닝하나

카테고리별로 어느 신호가 더 유리한지 다를 수 있다는 가설:

- `head` (단일 토큰) → 키워드 매칭이 워낙 신뢰도 높음. keyword 가중치를 올리면 유리할 것.
- `long_natural` → 긴 자연어는 리터럴 토큰 매칭보다 의미 임베딩이 유리. vector 가중치를 올리면 유리할 것.
- `short_korean` → 어느 쪽이 이길지 애매.

환경변수로 override 할 수 있도록 Spring `@ConfigurationProperties` 의 relaxed binding 을 활용:

```bash
RAG_SEARCH_KEYWORDWEIGHT=0.6 RAG_SEARCH_VECTORWEIGHT=0.4 \
  EVAL_LABEL=tune-kw60 ./gradlew evalTest
```

세 지점 그리드 (합계 1.0 로 해석 용이):

| 가중치 (kw / vec) | Recall@10 | nDCG@10 | P@5 | MRR |
|---|---|---|---|---|
| `tune-kw60` (0.6 / 0.4) | 0.742 | 0.849 | 0.669 | 0.904 |
| **`phase1-2` (0.5 / 0.5)** | **0.826** | **0.926** | **0.738** | 0.940 |
| `tune-kw40` (0.4 / 0.6) | 0.808 | 0.913 | 0.708 | **0.971** |

카테고리별 Recall@10:

| 카테고리 | kw60 | phase1-2 | kw40 |
|---|---|---|---|
| head (n=6) | 0.678 | 0.694 | 0.678 |
| short_korean (n=10) | 0.719 | **0.880** | 0.854 |
| long_natural (n=6) | 0.568 | **0.718** | 0.706 |
| synonym (n=4) | **0.970** | 0.939 | 0.909 |
| adversarial (n=4) | 1.000 | 1.000 | 1.000 |

### 4.3 결론

- **기본값 `0.5 / 0.5` 가 이미 sweet spot**. 세 주요 메트릭에서 모두 1위.
- 키워드-heavy (0.6/0.4) 는 `short_korean` / `long_natural` 양쪽 모두 큰 손실. OR 매칭이 과하게 퍼지면서 부분 매칭 쓰레기가 상위를 먹음.
- 벡터-heavy (0.4/0.6) 는 MRR 이 살짝 낫지만 전체 Recall 이 2%p 떨어짐.
- **`head` 는 어느 지점에서도 flat** — 이건 가중치로 풀 수 있는 문제가 아니라 `ts_rank_cd` 가 단일 토큰 쿼리에서 candidate pool 순서를 결정해버리는 구조적 문제. 더 올리려면 다른 레버(HyDE 같은 쿼리 재작성)가 필요할 것으로 보였고, 그게 Phase 2 의 동기가 됐다.

---

## 5. Phase 2 — HyDE 시도와 기각

### 5.1 HyDE 란

**Hypothetical Document Embeddings**. 원 논문(Gao et al. 2022)의 아이디어는 간단하다.

1. 사용자 쿼리 ("인턴") 를 LLM 에 넣어 **가상의 "관련된 문서"** 를 생성 (예: "OO 회사에서 2025년 하반기 인턴을 모집합니다…" 같은 가짜 공고).
2. 이 가짜 문서를 임베딩해서 벡터 검색에 사용.
3. 쿼리 임베딩과 실제 문서 임베딩 사이의 거리보다, **가짜 문서 임베딩과 실제 문서 임베딩** 사이의 거리가 가까울 것이라는 가정.

직관은 "쿼리는 짧고 의도가 불완전한데, LLM 이 쿼리로부터 문서 형태의 '이상적 응답' 을 상상해주면 그 표현이 실제 문서 분포에 더 가까워진다".

### 5.2 Phase 2a — 순수 HyDE

`HydeService` 를 하나 만들어서 Gemini 가 한국어 대외활동 공고 템플릿 을 생성하게 했다. `ActivityHybridSearchService.fetchVectorResults` 에서 쿼리 임베딩 대신 **가짜 공고 임베딩** 을 pgvector 에 날림.

Feature flag (`rag.search.hydeEnabled=true`) 로 켜고 eval 돌림:

| Metric | phase1-2 | phase2-hyde | Δ |
|---|---|---|---|
| Recall@10 | 0.826 | 0.497 | **-40%** |
| nDCG@10 | 0.926 | 0.626 | -32% |
| P@5 | 0.738 | 0.492 | -33% |
| MRR | 0.940 | 0.788 | -16% |
| ZeroRate | 0.000 | 0.100 | +10pp |
| p50 | 18.0s | 22.4s | +24% |

전 카테고리에서 regression. 특히 `long_natural` 이 **-64%** 로 가장 심했다.

### 5.3 왜 실패했나 — 외부 API 문제가 아니다

처음엔 "Gemini rate limit 때문에 엉뚱한 결과가 나온 건가?" 싶었다. 확인해보니 429 에러는 `synonym` / `adversarial` 끝자락 쿼리 3개에만 떨어졌고, **clean 한 카테고리(head, short_korean, long_natural) 들은 정상 호출됐는데도 regression**. 진짜 원인은 모델-코퍼스 misfit 이었다.

1. **스타일 불일치**. 한국어 대학생 대외활동 공고는 형식이 강하다 — 모집대상 / 활동내용 / 주관 / 혜택 같은 정해진 섹션. Gemini 는 서술형 내러티브 산문을 생성 → 임베딩 공간에서 실제 공고들과 거리가 멀어짐.
2. **짧은 쿼리에서 환각 드리프트**. "인턴" 한 단어를 주면 LLM 이 임의의 회사명 / 구체 직무를 지어내 생성. 가짜 문서가 특정 niche 에 narrowing 되면서 broad match 를 놓침.
3. **쿼리 의미를 완전히 버린다**. 순수 HyDE 는 `RETRIEVAL_QUERY` 임베딩을 아예 안 쓴다. `long_natural` 처럼 이미 쿼리 자체가 풍부한 경우, 가장 강한 신호를 버리는 셈 — 그래서 -64%.
4. **비대칭 임베딩 헤드**. `gemini-embedding-001` 은 `RETRIEVAL_QUERY` / `RETRIEVAL_DOCUMENT` 두 taskType 으로 다른 표현을 낸다. 원 HyDE 논문은 대칭 인코더(Contriever 등) 가정인데, 이 모델은 그게 아니다. 쿼리 자리를 가짜 문서 임베딩으로 갈아끼우면 Q/D 공간 정렬이 깨진다.

### 5.4 Phase 2b — 앙상블 변종

근거 #3 이 가장 납득 가능한 원인이었다. 논문에서도 asymmetric retriever 와 결합할 때는 query embedding 을 버리지 않고 **평균** 내라고 한다. 그대로 해봤다.

```kotlin
val q = embed(query, "RETRIEVAL_QUERY")
val d = embed(pseudoPosting, "RETRIEVAL_DOCUMENT")
val ensemble = elementWiseMean(q, d).l2Normalize()
repository.findByVectorSimilarity(ensemble.toVectorString(), limit)
```

결과 — 순수 HyDE 보다는 확실히 나아졌지만, 여전히 phase1-2 대비 regression.

| Metric | phase1-2 | phase2-hyde | phase2-hyde-ens | Δ vs phase1-2 |
|---|---|---|---|---|
| Recall@10 | 0.826 | 0.497 | 0.744 | **-10%** |
| nDCG@10 | 0.926 | 0.626 | 0.839 | -9% |
| P@5 | 0.738 | 0.492 | 0.662 | -10% |
| MRR | 0.940 | 0.788 | 0.917 | -2% |
| p50 | 18.0s | 22.4s | 23.8s | +32% |

카테고리별:

| 카테고리 | phase1-2 | phase2-hyde-ens | Δ |
|---|---|---|---|
| head (n=6) | 0.694 | 0.592 | **-15%** |
| short_korean (n=10) | 0.880 | 0.771 | **-12%** |
| **long_natural (n=6)** | 0.718 | **0.768** | **+7%** |
| synonym (n=4) | 0.939 | 0.677 | **-28%** |
| adversarial (n=4) | 1.000 | 1.000 | 0 |

- 앙상블은 "쿼리 의미 드랍" 문제를 해결 → `long_natural` 만 +7% 로 실제 이득.
- 하지만 `head` / `short_korean` / `synonym` 에서는 여전히 환각된 가짜 문서가 임베딩을 niche 로 잡아당김.
- 게다가 **latency 는 오히려 더 나빠짐** — 앙상블은 임베딩 호출이 두 번(Q + D) + Gemini 가짜 문서 생성까지 필요.

### 5.5 HyDE 기각

- `long_natural` 하나 +7% 따자고 나머지 네 카테고리 -10~28% 를 감수할 수 없음.
- `long_natural` 은 Phase 1 에서도 가장 개선폭이 작았던 카테고리 — 가장 작은 기반 위에서 가장 작은 이익을 주는 레버는 우선순위가 낮다.
- 코드 전량 revert. `ActivityHybridSearchService`, `RagProperties`, `GeminiService` 되돌리고 `HydeService.kt` 삭제.

### 5.6 이 코퍼스에서 HyDE 를 살리려면?

향후 참고용으로만 기록:

1. **대칭 임베딩 모델** 로 교체 — single-head 로 Q/D 구분 없으면 앙상블 수학이 깨지지 않음.
2. **템플릿 형식 프롬프트** — "모집대상 / 활동내용 / 혜택" 섹션 포맷을 강제해서 코퍼스 스타일에 맞춤. 대신 프롬프트 엔지니어링 유지비가 붙음.
3. **쿼리 분류 게이트** — `long_natural` 로 분류된 것만 HyDE 적용. 복잡도 증가 대비 이득이 20% 쿼리에만 +7% 수준. 다른 레버를 먼저 시도할 게 많음.

### 5.7 교훈

- HyDE 는 **문서 분포** 와 **모델** 양쪽에 민감하다. 논문의 셋업이 내 프로젝트와 다르다면 그대로 포팅하면 안 된다.
- 실패도 **숫자로 남기면 자산** 이다. 다음에 "HyDE 써볼까?" 라는 충동이 들 때, "해봤는데 이 코퍼스에서 -10% 였다" 는 한 줄로 시간을 아낄 수 있다.

---

## 6. 최종 점수판

| Run | Recall@10 | nDCG@10 | P@5 | MRR | p50 |
|---|---|---|---|---|---|
| baseline-2 (동일 코퍼스) | 0.794 | 0.897 | 0.715 | 0.939 | 17.2s |
| **phase1-2** (ship) | **0.826** | **0.926** | **0.738** | 0.940 | 18.0s |
| phase2-hyde | 0.497 | 0.626 | 0.492 | 0.788 | 22.4s |
| phase2-hyde-ens | 0.744 | 0.839 | 0.662 | 0.917 | 23.8s |

**최종 shipping 변경사항**: Phase 1 키워드 쿼리 개선 (AND → OR+prefix, `ts_rank` → `ts_rank_cd`, ILIKE fallback 제거). 커밋 한 줄로 요약할 수 있을 만큼 작은 변경이지만 숫자로는 Recall +4%.

---

## 7. 전체 회고

### 7.1 잘한 것

- **평가 하네스를 먼저 만들었다**. Phase 1 에서 false regression(corpus drift) 을 잡아낼 수 있었던 것도, Phase 2 의 "실패를 확신 있게 기각" 할 수 있었던 것도 모두 하네스 덕분.
- **카테고리 분할**. 평균만 봤으면 Phase 2b 가 "약간 나쁨" 정도로 보였을 거고 더 질질 끌었을 것. `long_natural` 만 오르고 나머지가 다 떨어지는 패턴이 수치로 명백했다.
- **코퍼스 스냅샷 추적**. JSON 마다 `snapshotDate` + `commit` 을 박아서 사후 검증 가능.

### 7.2 못한 것 / 다음에 할 것

- 30개 쿼리는 통계적으로 여전히 부족. 특히 카테고리별 n=4~6 은 쿼리 한두 개가 카테고리 평균을 왜곡할 수 있다.
- `marginalIds` (1점 라벨) 를 지금은 버리고 있다. graded nDCG (gain: 2점=1.0, 1점=0.5) 로 확장하면 순위 품질을 더 섬세하게 측정 가능.
- Job 검색(`HybridSearchService`) 에도 같은 방법론 적용 필요.
- **비교 리포트 자동화** — baseline.json vs 최신 JSON 을 diff 하는 `EvalComparisonTest` 추가, CI 에 붙이면 PR 별 자동 품질 리포트 가능.

### 7.3 감만으로 "좋아진 것 같다" 라고 말하는 것과, "04-20 코퍼스에서 short_korean 카테고리 Recall@10 이 0.819 → 0.880 로 +6.1pp 올랐다" 라고 말하는 것의 차이

후자는 revert 해야 할지, 다음 phase 로 넘어가야 할지, PR 을 열어도 되는지 판단할 수 있게 해준다. 전자는 그냥 자기만족이다.

검색 개선은 랜덤워크처럼 보이지만, 하네스가 있으면 그래디언트를 볼 수 있다.

---

## 참고 문헌

- Voorhees, E. M. (2001). *The Philosophy of Information Retrieval Evaluation*. TREC.
- Gao, L. et al. (2022). *Precise Zero-Shot Dense Retrieval without Relevance Labels* (HyDE 원 논문).
- Järvelin, K., & Kekäläinen, J. (2002). *Cumulated gain-based evaluation of IR techniques* (nDCG).
- Cormack, G. V., Clarke, C. L., & Büttcher, S. (2009). *Reciprocal Rank Fusion outperforms Condorcet and individual rank learning methods*. SIGIR.
