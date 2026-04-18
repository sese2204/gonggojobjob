# 검색 품질 평가 하네스 (Search Evaluation Harness)

`ActivityHybridSearchService`의 검색 품질 변화를 **재현 가능한 숫자**로 비교하기 위한 오프라인 평가 프레임워크. 짧은 한국어 질의("인턴 IT" 등)에서 공모전 위주로만 검색되는 문제를 해결하는 과정에서, 각 개선이 실제로 품질을 올리는지 판단하기 위해 도입.

---

## 1. 방법론 개요

### 1.1 Information Retrieval 평가의 기본 전제

검색은 분류(classification) 문제가 아니라 **랭킹(ranking) 문제**다. "정답 한 개"가 아니라 "관련 있는 문서들이 상위에 얼마나 잘 왔는가"를 본다. 따라서:

- 정답은 집합(set)이지, 단일 값이 아님
- 순위(rank)가 품질의 일부임 — 같은 문서라도 1위에 있는 것과 10위에 있는 것은 가치가 다름
- 메트릭도 랭킹 기반 지표(nDCG, MRR)와 집합 기반 지표(Recall, Precision)를 함께 본다

### 1.2 우리가 택한 방식: Pooled Relevance

DB에 수천 건이 있는 상황에서 전수 라벨링은 불가능하다. 그래서 **TREC 방식의 pooled relevance** 를 채택:

1. 쿼리별로 "검색 결과 후보 pool"을 만든다 (현재 검색 top-N + 보조 쿼리 결과 합집합)
2. 이 pool 안에서만 관련도(0/1/2)를 채점
3. Pool에 들어오지 않은 문서는 평가 대상에서 제외 (암묵적으로 관련없음 취급)

**강점**: 실전에서 적용 가능한 규모의 라벨링 부담.
**약점**: Pool 밖 진짜 관련 문서는 영영 발굴 못 함. 새 알고리즘이 그걸 찾아내도 "관련 없는 걸 찾았다"고 잘못 평가됨.
**완화**: Pool 생성을 여러 방법(하이브리드 검색 + term별 ILIKE)의 합집합으로 해서 다양성 확보.

### 1.3 하이브리드 라벨링: LLM + 본인

- **1단계 (자동)**: Gemini에게 각 (쿼리, 후보) 쌍에 대해 0/1/2 점수 요청
- **2단계 (수작업)**: 본인이 2점 받은 것 + 애매한 것(1점)을 눈으로 검수

LLM은 recall-oriented 역할 — 놓치지 않게 후보를 스크리닝. 최종 판단은 사람.

### 1.4 상대 비교 원칙

30개 쿼리로는 **절대 점수**(예: "nDCG 0.65는 좋은가?")를 논할 통계적 근거가 부족하다. 우리가 보는 건:

- **Before vs After 차이** (baseline → phase1 → phase2)
- **카테고리별 변화** (short_korean만 올라가면 충분)
- **Regression 감지** (평균은 올랐는데 head 쿼리가 깨졌는지)

따라서 eval set은 **"안 바뀌는 기준자"** 역할이 핵심이다.

---

## 2. 디렉터리 구조

```
src/test/kotlin/org/example/kotlinai/eval/
  EvalMetrics.kt          ← 순수 메트릭 함수 (recall@K, nDCG, MRR, P@K)
  EvalMetricsTest.kt      ← 메트릭 단위 테스트
  EvalModels.kt           ← 데이터 클래스 + YAML 로더 + JSON writer
  SearchEvalTest.kt       ← 30쿼리 실행 → JSON 리포트 (@Tag("eval"))
  LabelingHelperTest.kt   ← Gemini 채점 + 검수용 YAML 출력 (@Tag("eval-label"))
  README.md               ← 본 문서

src/test/resources/eval/
  eval-queries.yaml       ← 30개 쿼리 + 골드라벨 (관리 대상, git 체크인)
  results/                ← SearchEvalTest 출력 JSON (gitignored, baseline만 수동 체크인)
  labeling/               ← LabelingHelperTest 출력 YAML (gitignored)

src/test/resources/
  application-eval.yml    ← eval 프로파일 (Postgres + Gemini)
```

---

## 3. 메트릭 정의

모든 메트릭은 쿼리 1건당 계산 후 전체 평균. 카테고리별 평균도 별도 산출.

### 3.1 Recall@K

> top-K 안에 포함된 관련 문서 수 ÷ 전체 관련 문서 수

"놓치지 않았는가"를 측정. **가장 중요한 지표** — 현재 "인턴 치면 인턴이 안 뜬다"는 문제가 바로 recall 문제다.

```
returned: [7, 42, 99, 3, 18]   relevant: {3, 7, 55}
Recall@10 = |{7, 3}| / |{3, 7, 55}| = 2/3 ≈ 0.67
```

`K=10` 기본값. 사용자가 실제로 스캔하는 범위 추정.

### 3.2 nDCG@K (normalized Discounted Cumulative Gain)

> 관련 문서가 상위에 있을수록 가산, log2 할인으로 하위 rank 페널티. Ideal ordering 대비 비율.

순위 품질 측정. "같은 관련 문서를 찾았어도 1위에 있는지 5위에 있는지" 구분. **Gemini 재랭킹의 효과를 직접 보여주는 지표**.

```
perfect = 1.0
단순 hit 개수만 중요하다면 recall로 충분하지만, 사용자 체감은 순위에 크게 좌우됨
```

### 3.3 Precision@5

> top-5 중 관련 문서 비율

"상위에 쓰레기가 얼마나 섞였는가". **"공모전이 상위를 먹는 문제"를 직접 포착**하는 지표.

### 3.4 MRR (Mean Reciprocal Rank)

> 첫 관련 문서가 나타나는 순위의 역수 (1/rank), 없으면 0

사용자 첫인상. 1위에 바로 관련 문서가 뜨면 1.0, 5위면 0.2.

### 3.5 Zero-result rate

> 빈 결과가 나온 쿼리 비율

키워드 검색 AND 버그의 직접 지표. Phase 1(OR 전환) 효과를 단일 숫자로 보여줌.

### 3.6 Latency p50 / p95

> 쿼리별 응답 시간의 중앙값 / 95백분위

HyDE는 Gemini 호출을 추가하므로 지연 증가가 트레이드오프. nDCG가 올라가도 p95가 1초 이상 늘어나면 손익 계산 필요.

### 3.7 Label Coverage

> 라벨(relevantIds)이 있는 쿼리 비율

30개 쿼리 중 몇 개를 실제로 라벨링했는지. Adversarial 쿼리는 의도적으로 relevantIds 비워둠 (zero-result만 보면 됨).

---

## 4. Eval Set 구성

`eval-queries.yaml` — 30개 쿼리, 5 카테고리:

| 카테고리 | 개수 | 목적 |
|---|---|---|
| `head` | 6 | 자주 쓰이는 단일 토큰. Regression 감지 기준선 |
| `short_korean` | 10 | 2~3 토큰 한국어 조합. **현재 깨진 케이스 재현** |
| `long_natural` | 6 | 긴 자연어 질의. **HyDE 강점이 드러날 것으로 기대** |
| `synonym` | 4 | 약어/동의어/특수문자 처리 |
| `adversarial` | 4 | 오타, 무의미, 반복 등 실패 모드 |

**스키마**:
```yaml
- id: q007
  category: short_korean
  tags: ["인턴"]           # filter-like 의도
  query: "IT"              # free-text 의도
  relevantIds: [42, 87]    # 명백히 관련 있는 activity_listing.id
  marginalIds: [55]        # 애매한 것 (현재 미사용, graded nDCG용 예약)
  notes: "..."             # 주석 (실행에 영향 없음)
```

**라벨링 규칙 (rubric)**:
- **relevant (2점)**: 카테고리/직무/주제가 쿼리 의도에 정확히 부합. 사용자가 클릭할 만함.
- **marginal (1점)**: 키워드는 스치지만 주된 의도가 어긋남. 부차적.
- **irrelevant (0점)**: 카테고리가 완전히 다르거나 우연한 키워드 매칭.

---

## 5. 실행 가이드

### 5.1 선행 조건

- Postgres DB (pgvector 확장 필요) 접속 가능
- Gemini API 키
- `application-eval.yml`에 DB/키 값 설정됨 (현재 로컬 값 재사용 기본값 내장)

### 5.2 최초 1회: 라벨링

```bash
./gradlew evalLabel
```

**수행 내용** (`LabelingHelperTest`):
1. 30개 쿼리를 현재 `ActivitySearchService.search()`로 실행 → top-20 후보 수집
2. 각 쿼리 토큰으로 term별 `findByKeywordLike` 실행 → 추가 후보 수집
3. 합집합 dedupe → 쿼리당 최대 50개 후보 pool
4. Gemini에게 (쿼리, 후보)를 보내 0/1/2 점수 요청
5. `src/test/resources/eval/labeling/candidates-YYYY-MM-DD.yaml` 생성

**출력 예시**:
```yaml
queries:
  - id: q007
    category: short_korean
    tags: ["인턴"]
    query: "IT"
    candidates:
      - id: 42   # score=2
        title: "네이버 IT 인턴 모집"
        category: "IT"
        organizer: "네이버"
      - id: 103  # score=1
        title: "IT 스타트업 탐방"
        ...
      - id: 78   # score=0
        title: "...공모전..."
```

**수작업 검수** (쿼리당 5~7분):
- 각 쿼리의 candidates 블록을 읽고, score=2 + 본인이 보기에 맞다고 판단한 것의 id를 `eval-queries.yaml`의 `relevantIds`에 복사
- score=1도 관련 있다 싶으면 추가
- score=0이어도 본인 판단으로 관련 있으면 추가
- 어차피 pool 밖은 모르니, 놓친 게 있어도 같은 조건에서 비교하니 OK

### 5.3 Baseline 측정

```bash
EVAL_LABEL=baseline ./gradlew evalTest
```

**수행 내용** (`SearchEvalTest`):
1. `eval-queries.yaml` 로드
2. 쿼리별로 search cache clear → `ActivitySearchService.search()` 실행 → 응답 시간 측정
3. `returnedIds`를 `relevantIds`와 비교하여 각 메트릭 계산
4. 카테고리별 평균 + 전체 평균 집계
5. `src/test/resources/eval/results/baseline-YYYY-MM-DD.json` 생성

**출력 JSON**:
```json
{
  "label": "baseline",
  "timestamp": "2026-04-18T14:00:00",
  "commit": "429ef04",
  "snapshotDate": "2026-04-18",
  "summary": {
    "recallAt10Mean": 0.42,
    "ndcgAt10Mean": 0.38,
    "zeroResultRate": 0.20,
    "latencyP95Ms": 2800,
    ...
  },
  "byCategory": {
    "short_korean": { "recallAt10Mean": 0.25, ... },
    "head": { "recallAt10Mean": 0.70, ... }
  },
  "perQuery": [...]
}
```

### 5.4 개선 후 재측정

Phase 1 구현 → 체크아웃하고:
```bash
EVAL_LABEL=phase1 ./gradlew evalTest
```

Phase 2 구현 → :
```bash
EVAL_LABEL=phase2 ./gradlew evalTest
```

세 JSON을 `byCategory.short_korean.recallAt10Mean` 키로 비교 (간단 jq 스크립트):
```bash
for f in baseline phase1 phase2; do
  echo "=== $f ==="
  jq '.byCategory.short_korean | {recall: .recallAt10Mean, ndcg: .ndcgAt10Mean, zero: .zeroResultRate, p95: .latencyP95Ms}' \
    src/test/resources/eval/results/${f}-*.json
done
```

---

## 6. 핵심 설계 결정 & 이유

### 6.1 왜 Spring 풀 컨텍스트를 띄우는가?

`@SpringBootTest`로 실제 Bean 주입하여 `ActivitySearchService` 전체 체인 (repository → embedding → Gemini → cache) 이 프로덕션과 동일하게 돈다. Mock으로 좁히면 실제 DB 쿼리 결과를 못 보고, 그러면 keyword 쿼리 수정 효과를 측정 불가.

### 6.2 왜 eval 프로파일을 분리했나?

- 테스트 DB(H2) → pgvector 미지원, 실측정 불가
- `local` 프로파일 → 운영 데이터 접속권한 필요, ingestion 스케줄러가 돌아서 데이터가 바뀔 수 있음
- `eval` → `local`과 같은 DB를 보되 `spring.task.scheduling.enabled=false`로 백그라운드 작업 꺼서 **스냅샷 안정성 확보**

### 6.3 왜 쿼리별로 cache clear?

`SearchCacheService`가 5분 TTL로 결과를 보관. 같은 (tags, query) 반복 호출 시 캐시 hit → 두 번째 실행이 첫 번째와 동일해짐. Reflection으로 `cache` 필드를 매 쿼리마다 초기화.

### 6.4 왜 DailySearchLimit을 안 건드렸나?

`ActivitySearchService`는 `SecurityContextHolder`의 auth가 null이면 limit 체크를 건너뛴다. `SearchEvalTest`에서 `SecurityContextHolder.clearContext()` 호출만으로 자동 우회. 주 코드 수정 불필요.

### 6.5 왜 첫 호출 latency를 측정하나?

사용자 체감 = 첫 호출. 캐시 hit은 사용자 입장에서 "빠름"으로 당연히 기대되는 것이라 측정 의미가 적다. HyDE는 첫 호출에만 Gemini 추가 호출이 들어가므로, cold path 지연을 봐야 트레이드오프가 정확히 드러난다.

### 6.6 왜 LLM-as-Judge를 쓰는가 (환각 리스크가 있는데)?

- 1,200+ 쌍 라벨링을 사람이 하면 현실성 없음
- LLM은 "명백히 무관한 것"을 잘라내는 데 특히 정확 (precision 문제엔 약해도 screening엔 충분)
- 최종 판단은 사람이라 환각이 라벨에 바로 반영되진 않음
- 비용: 1회 라벨링 ~$0.3 수준 (Gemini Flash 기준)

### 6.7 왜 Gemini HyDE 결과를 Gemini 채점자가 평가하면 문제인가?

"자기 생성물에 호의적" 편향 (self-preference bias) 가능. 이번은 Gemini 단일 provider라 이 위험을 감수하되, 중요한 결정 전에는 샘플 20개를 본인이 직접 검수하여 편향 체크 권장. 향후 필요 시 채점자를 다른 모델로 교체.

---

## 7. 한계 & 알려진 리스크

| 리스크 | 영향 | 완화책 |
|---|---|---|
| Pool 밖 문서는 영영 라벨링 안 됨 | Recall 과대/과소 평가 | Pool을 여러 검색법의 합집합으로; 필요 시 iterative labeling |
| DB row 삭제로 relevantId가 stale | Recall 가짜 하락 | `snapshotDate` 기록, `@Scheduled` 비활성화, 삭제 감지 로그 |
| 30개는 통계적으로 적음 | 개별 수치 신뢰도 낮음 | 절대값 아닌 **변화 방향**만 신뢰 |
| LLM 채점자 편향 | 라벨 품질 저하 | 최종 판단 사람 검수; 결과가 이상할 땐 재라벨링 |
| 캐시/limit 우회는 리플렉션 의존 | 주 코드 리팩토링 시 깨짐 | clearCache 실패 시 테스트가 assert 없이도 결과 이상하게 나올 위험 — 추후 주 코드에 `@Profile("!eval")` 으로 cache 비활성화 bean 고려 |
| Gemini rate limit | 라벨링/eval 실패 | `runCatching`으로 감싸 쿼리별 skip; 필요 시 `Thread.sleep` 페이싱 |

---

## 8. 확장 포인트

### 8.1 Job 검색 eval 추가

현재는 Activity만 평가. `HybridSearchService`(Job)도 같은 문제가 있을 가능성. 추가 시:
- `eval-queries-job.yaml` 별도 파일
- `JobSearchEvalTest` 추가, `JobSearchService` 주입
- 메트릭 공통 재사용

### 8.2 Graded Relevance (1점도 가중 반영)

현재는 binary (2점만 relevantIds). `marginalIds`(1점)를 nDCG gain에 0.5로 반영하면 더 미세한 순위 품질 측정 가능. `EvalMetrics.ndcgAtK`에 gain map을 받도록 확장.

### 8.3 비교 리포트 자동화

`EvalComparisonTest`를 추가하여 `baseline.json` vs 최신 JSON을 diff해서 마크다운 리포트 출력. CI에 붙이면 PR별 자동 품질 리포트.

### 8.4 A/B feature flag 조합 평가

RagProperties 플래그(`hydeEnabled`, 키워드 모드) 조합별로 4가지 변종을 같은 DB 스냅샷에 대해 돌려 비교. 환경변수로 플래그 override 지원 시 단일 커밋에서 여러 라벨 생성 가능.

---

## 9. 참고 문헌

- Voorhees, E. M. (2001). *The Philosophy of Information Retrieval Evaluation*. TREC.
- Gao, L. et al. (2022). *Precise Zero-Shot Dense Retrieval without Relevance Labels*. (HyDE 원 논문)
- Järvelin, K., & Kekäläinen, J. (2002). *Cumulated gain-based evaluation of IR techniques*. (nDCG)
