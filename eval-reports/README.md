# Search Quality Eval Reports

Rolling record of retrieval-quality experiments on the Activity hybrid search path
(`ActivityHybridSearchService` + pgvector + pg_trgm).

## Layout

- `runs/` — raw JSON reports emitted by `SearchEvalTest`, organized by round.
  Each round has its own labeling snapshot (re-labeling starts a new round).
  Source copy lives under `src/test/resources/eval/results/<round>/`.
  - `runs/round-1/` — 1차 테스트 (labeling snapshot: 2026-04-20, corpus drift noted)
- `README.md` (this file) — current metrics table + changelog.

### Round 개념

- **라운드** = 동일한 `eval-queries.yaml` 라벨링 스냅샷을 사용하는 실행 묶음.
- corpus drift(매일 공고 업데이트)로 인해 라운드를 넘나드는 비교는 의미 없음.
- 새 라운드 시작 조건: `LabelingHelperTest`로 재라벨링 후 `eval-queries.yaml` 갱신.
- 새 라운드 디렉토리: `runs/round-N/`, `src/test/resources/eval/results/round-N/`.

## How runs are produced

```bash
EVAL_LABEL=<label> ./gradlew evalTest
```

- Evaluates 30 queries from `src/test/resources/eval/eval-queries.yaml`.
- Writes both `src/test/resources/eval/results/<label>-<snapshotDate>.json`
  and (manually) a copy into `eval-reports/runs/`.
- Label coverage `0.87` = 4 queries intentionally have empty `relevantIds`
  (excluded from averages; run for latency / zero-rate only).

## Methodology notes

- **Pooled relevance (TREC-style)**: candidate pool generated from the current
  algorithm, then labeled by Gemini flash (0/1/2).
- **Strict threshold**: only score=2 items enter `relevantIds`.
- **Corpus drift is real**: the ingestion scheduler runs daily, so runs across
  days are **not** directly comparable. Always pair an algorithm change with a
  same-day baseline rerun (see `baseline-2` below).

## Pipeline reference

```
query + tags
   ├── fetchVectorResults ──► pgvector cosine similarity ──► top N×2 by embedding
   └── fetchKeywordResults ─► Postgres FTS (to_tsquery + ts_rank_cd) ──► top N×2 by rank
                                  │
                                  ▼
                          mergeByRrf (Reciprocal Rank Fusion)
                                  │
                                  ▼
                              top N results
```

Each candidate gets score = `Σ weight_i × 1 / (k + rank_i)` over the lists it
appears in, where `k = 60` (industry default, prevents top ranks from
dominating). Listings outside a given list contribute 0 from that list.

## RRF weight tuning

`ActivityHybridSearchService.mergeByRrf` combines the two ranked lists:

```kotlin
score(doc) = vectorWeight  × 1 / (60 + vecRank)
           + keywordWeight × 1 / (60 + kwRank)
```

Weights live in `RagProperties` (`rag.search.vectorWeight` / `.keywordWeight`),
defaulting to `0.5 / 0.5` (equal).

**Why tune**: categories don't all prefer the same signal.
- `head` (single-token queries like "인턴") — keyword match is highly reliable;
  raising `keywordWeight` should help.
- `long_natural` ("백엔드 개발 직무 체험 프로그램") — embeddings capture intent
  better than literal token matches; raising `vectorWeight` should help.
- `short_korean` (2~3 tokens) — mixed, depends on query.

**How to tune**: override via environment variables (Spring Boot relaxed binding
maps `RAG_SEARCH_KEYWORDWEIGHT` → `rag.search.keywordWeight`). Gradle's Test
task inherits the parent env, so this reaches the Spring context without extra
config:

```bash
RAG_SEARCH_KEYWORDWEIGHT=0.6 RAG_SEARCH_VECTORWEIGHT=0.4 \
  EVAL_LABEL=tune-kw60 ./gradlew evalTest
```

Grid explored on 2026-04-20 (weights sum to 1.0 for interpretability):

| Weights (kw / vec) | Recall@10 | nDCG@10 | P@5 | MRR |
|---|---|---|---|---|
| `tune-kw60` (0.6 / 0.4) | 0.742 | 0.849 | 0.669 | 0.904 |
| **`phase1-2` (0.5 / 0.5)** | **0.826** | **0.926** | **0.738** | 0.940 |
| `tune-kw40` (0.4 / 0.6) | 0.808 | 0.913 | 0.708 | **0.971** |

By category (Recall@10):

| Category | kw60 | phase1-2 | kw40 |
|---|---|---|---|
| head (n=6) | 0.678 | 0.694 | 0.678 |
| short_korean (n=10) | 0.719 | **0.880** | 0.854 |
| long_natural (n=6) | 0.568 | **0.718** | 0.706 |
| synonym (n=4) | **0.970** | 0.939 | 0.909 |
| adversarial (n=4) | 1.000 | 1.000 | 1.000 |

**Conclusion**: the default `0.5 / 0.5` is already the sweet spot.
- Keyword-heavy (0.6/0.4): big loss across short_korean and long_natural
  (keyword OR query dominates and surfaces partial matches). Only helps `synonym`.
- Vector-heavy (0.4/0.6): slightly better MRR and `short_korean` is still solid,
  but `synonym` suffers and overall Recall drops ~2%.
- `head` is flat everywhere — the single-token case is bottlenecked by the
  corpus and `ts_rank_cd` ordering, not by the weight. Further head gains
  will need a different lever (e.g., Phase 2 HyDE or query rewriting).

## Runs

### Round 2 — labeling snapshot 2026-04-22 (`runs/round-2/`)

**목표**: Gemini 임베딩(768d) vs Upstage Solar 임베딩(4096d) A/B 비교.
Upstage는 한국어 특화 + 고차원이라 우위가 예상됐으나 결과는 반대.

| Label | Recall@10 | nDCG@10 | P@5 | MRR | p50 |
|---|---|---|---|---|---|
| `round2-gemini` | **0.834** | **0.955** | **0.728** | **0.960** | 17.3s |
| `round2-upstage` | 0.737 | 0.833 | 0.600 | 0.947 | 17.6s |

By category (Recall@10):

| Category | round2-gemini | round2-upstage |
|---|---|---|
| head (n=6) | **0.703** | — |
| short_korean (n=10) | **0.825** | — |
| long_natural (n=6) | **0.844** | — |
| synonym (n=4) | **0.933** | — |
| adversarial (n=4) | **1.000** | **1.000** |

**⚠️ 결과를 신뢰할 수 없음 — 라벨 편향(label bias) 문제**

Round 2 라벨링은 `activitySearchService.search()`(기본 provider = Gemini)의
검색 결과를 후보 풀로 사용해 Gemini가 0/1/2 채점했다.
즉, **relevantIds가 Gemini가 찾은 문서로만 구성**됨.

Upstage가 Gemini와 다른 관련 문서를 찾아와도 라벨 후보에 없기 때문에
true positive로 인정받지 못한다. eval 자체가 Gemini 편향.

**수정 방향 (Round 3)**:
- 라벨링 후보 풀 = Gemini hybrid pool + Upstage vector pool + ILIKE pool
- 두 모델이 각자 찾는 문서를 모두 포함시킨 뒤 Gemini LLM이 공정하게 채점
- `LabelingHelperTest` 수정 완료: `poolIds = (geminiPool + upstagePool + ilikePool).distinct().take(80)`

### Round 1 — labeling snapshot 2026-04-20 (`runs/round-1/`)

| Label | Date | Commit | Corpus snapshot | Notes |
|---|---|---|---|---|
| `baseline` | 2026-04-18 | 75eb6a7 | 2026-04-18 | First labeled run. Baseline = AND-joined `to_tsquery` + `ts_rank` + ILIKE fallback. |
| `phase1` | 2026-04-20 | 75eb6a7 | 2026-04-20 | OR+prefix `to_tsquery` + `ts_rank_cd`, no ILIKE fallback. Looked like regression — turned out to be corpus drift. |
| `baseline-2` | 2026-04-20 | 75eb6a7 | 2026-04-20 | Baseline code re-measured on current corpus with v2 labels. This is the fair comparison point for `phase1`. |
| `phase1-2` | 2026-04-20 | 75eb6a7 | 2026-04-20 | Phase 1 code re-measured with v2 labels. Fair comparison vs `baseline-2`. |
| `tune-kw60` | 2026-04-20 | 75eb6a7 | 2026-04-20 | Phase 1 + RRF weights (kw=0.6, vec=0.4). Keyword-heavy. |
| `tune-kw40` | 2026-04-20 | 75eb6a7 | 2026-04-20 | Phase 1 + RRF weights (kw=0.4, vec=0.6). Vector-heavy. |
| `phase2-hyde` | 2026-04-21 | 6e90632 | 2026-04-20 | Pure HyDE: vector search uses pseudo-posting embedding only, original query embedding dropped. **Failed** — see changelog. |
| `phase2-hyde-ens` | 2026-04-21 | 6e90632 | 2026-04-20 | HyDE ensemble: average(embed(query, RETRIEVAL_QUERY), embed(pseudo, RETRIEVAL_DOCUMENT)). Strict improvement over `phase2-hyde` but still regresses vs `phase1-2`. **Rejected** — see changelog. |

## Current scoreboard

All metrics computed against `eval-queries.yaml` v2 (snapshotDate `2026-04-20`,
score=2 only). `labelCov 0.87`, 30 queries.

| Run | Recall@10 | nDCG@10 | P@5 | MRR | p50 latency |
|---|---|---|---|---|---|
| baseline-2 (same corpus) | 0.794 | 0.897 | 0.715 | 0.939 | 17.2s |
| **phase1-2** | **0.826** | **0.926** | **0.738** | 0.940 | 18.0s |
| Δ (algo only) | +4.0% | +3.2% | +3.2% | flat | +0.8s |

By category (Recall@10, phase1-2 vs baseline-2):

| Category | baseline-2 | phase1-2 | Δ |
|---|---|---|---|
| head (n=6) | 0.687 | 0.694 | +0.7pp |
| **short_korean (n=10)** | 0.819 | **0.880** | **+6.1pp** |
| **long_natural (n=6)** | 0.668 | **0.718** | **+5.0pp** |
| synonym (n=4) | 0.939 | 0.939 | 0 |
| adversarial (n=4) | 1.000 | 1.000 | 0 |

## Changelog

### 2026-04-21 — Phase 2b: HyDE ensemble → rejected, HyDE abandoned

**Change**: instead of replacing the query embedding, *average* it with the
pseudo-posting embedding. Uses correct asymmetric taskTypes:
`embed(query, RETRIEVAL_QUERY)` + `embed(pseudo, RETRIEVAL_DOCUMENT)`, then
element-wise mean → L2-renormalize.

**Result**: strict improvement over pure HyDE, but still net regression vs
`phase1-2`.

| Metric | phase1-2 | phase2-hyde | phase2-hyde-ens | Δ vs phase1-2 |
|---|---|---|---|---|
| Recall@10 | 0.826 | 0.497 | 0.744 | **-10%** |
| nDCG@10 | 0.926 | 0.626 | 0.839 | -9% |
| P@5 | 0.738 | 0.492 | 0.662 | -10% |
| MRR | 0.940 | 0.788 | 0.917 | -2% |
| ZeroRate | 0.000 | 0.100 | 0.000 | flat |
| p50 latency | 18.0s | 22.4s | 23.8s | +32% |

By category (Recall@10):

| Category | phase1-2 | phase2-hyde-ens | Δ |
|---|---|---|---|
| head (n=6) | 0.694 | 0.592 | **-15%** |
| short_korean (n=10) | 0.880 | 0.771 | **-12%** |
| **long_natural (n=6)** | 0.718 | **0.768** | **+7%** |
| synonym (n=4) | 0.939 | 0.677 | **-28%** |
| adversarial (n=4) | 1.000 | 1.000 | 0 |

**Interpretation**:
- Ensemble fixes the "query semantics dropped" problem from pure HyDE — `long_natural`
  actually gains +7% because rich natural queries benefit from the extra doc-side context.
- Every other category regresses. The pseudo-doc still drags the embedding
  toward a hallucinated niche for short/ambiguous queries ("인턴", "ML"), which
  is where `head` and `synonym` lose badly.
- Latency gets worse, not better: ensemble requires *two* embedding calls
  (query + pseudo) plus the LLM pseudo-posting generation.

**Decision**: HyDE rejected for this corpus + embedding model combo. The net
Recall loss isn't worth the single `long_natural` win — and `long_natural` was
already Phase 1's smallest win category, so the trade compounds badly.

**HyDE code reverted** from `ActivityHybridSearchService`, `RagProperties`, and
`GeminiService`; `HydeService.kt` deleted. Phase 1 (`phase1-2`) is the shipping
state.

**What would make HyDE viable here** (not pursuing now):
- A symmetric embedding model (single head) so ensemble math is less lossy.
- A prompt that generates *template-shaped* pseudo-postings (모집대상 / 활동내용 / ...)
  to match corpus style — but that reintroduces brittleness and cost.
- Query-classification gate: only apply HyDE for `long_natural`. Adds complexity
  for a +7% win on 20% of queries. Not worth it vs. pursuing a different lever.

### 2026-04-21 — Phase 2a: pure HyDE → rejected

**Change**: `fetchVectorResults` replaces the query embedding with an embedding
of a Gemini-generated pseudo-posting (`HydeService.generatePseudoPosting`).
Flag `rag.search.hydeEnabled` (default `false`); eval run with flag on.

**Result**: regression everywhere.

| Metric | phase1-2 | phase2-hyde | Δ |
|---|---|---|---|
| Recall@10 | 0.826 | 0.497 | -40% |
| nDCG@10 | 0.926 | 0.626 | -32% |
| P@5 | 0.738 | 0.492 | -33% |
| MRR | 0.940 | 0.788 | -16% |
| ZeroRate | 0.000 | 0.100 | +10pp |
| p50 latency | 18.0s | 22.4s | +24% |

By category — the three clean categories (no 429 rate-limit hits) all regressed:

| Category | ZeroRate | phase1-2 | phase2-hyde | Δ |
|---|---|---|---|---|
| head (n=6) | 0.000 | 0.694 | 0.521 | **-25%** |
| short_korean (n=10) | 0.000 | 0.880 | 0.666 | **-24%** |
| long_natural (n=6) | 0.000 | 0.718 | 0.256 | **-64%** |
| synonym (n=4) | 0.250 | 0.939 | 0.556 | contaminated by 429 |
| adversarial (n=4) | 0.500 | 1.000 | 0.286 | contaminated by 429 |

**Root cause — corpus/model fit, not API failure**:
1. **Style mismatch**: Korean student activity postings follow a rigid template
   (모집대상 / 활동내용 / 주관 / 혜택). Gemini generates narrative, generic prose.
   Embedding-space distance to real postings increases.
2. **Hallucination drift on short queries**: for "인턴" the LLM invents a
   specific company / domain. The pseudo-doc embedding narrows onto that
   niche and misses broad matches.
3. **Query semantics dropped**: pure-HyDE discards the `RETRIEVAL_QUERY`
   embedding of the user's original text. For `long_natural` (already rich
   natural queries) this removes the strongest signal — hence the -64% drop.
4. **Asymmetric embedding heads**: `gemini-embedding-001` has separate
   `RETRIEVAL_QUERY` / `RETRIEVAL_DOCUMENT` heads. The original HyDE paper
   assumes a symmetric encoder; this model doesn't behave that way.

The 429 rate-limit errors only hit `synonym` and `adversarial` (3 queries
total). The 25–64% drops in the clean head/short/long categories are the
actual signal.

**Next attempt — Phase 2b (ensemble)**: keep the query embedding as the
primary signal, average it with the HyDE pseudo-posting embedding. Addresses
root cause #3 directly and is what the original HyDE paper does when
combined with an asymmetric retriever.

### 2026-04-20 — RRF weight grid (kw=0.4/0.5/0.6) → keep default 0.5/0.5

**Change**: ran evalTest at three weight points holding Phase 1 algorithm
constant. Result: `0.5 / 0.5` dominates on Recall@10, nDCG@10, and P@5. MRR
alone prefers `0.4 / 0.6` but the Recall drop isn't worth it. No config change.

### 2026-04-20 — Phase 1 accepted (pending commit)

**Change**: keyword query builder switched from `"token1 & token2"` (AND) to
`"token1:* | token2:*"` (OR + prefix) in `ActivityHybridSearchService.fetchKeywordResults`.
Order-by switched from `ts_rank` to `ts_rank_cd` in
`ActivityListingRepository.findByKeyword`. ILIKE fallback dropped.

**Result**: +4% Recall@10, +3% nDCG@10, +3% P@5 vs same-corpus baseline.
Wins concentrated in 2~3 token Korean and long natural-language queries,
which was the target failure mode ("인턴 IT" returning mostly 공모전).

**Caveat**: initial `phase1` run (vs `baseline` on 04-18 labels) looked like a
regression. Root cause was corpus drift — daily ingestion/cleanup changed the
indexed set between the two labeling passes. Re-labeling against the Phase 1
candidate pool and re-running baseline on the same corpus (`baseline-2`)
produced the fair comparison above.

### 2026-04-18 — Phase 0 / eval harness

Labeling harness wired up: `evalLabel` generates candidate pool per query,
Gemini scores 0/1/2, strict score=2 threshold lands in `eval-queries.yaml`.
`evalTest` produces the JSON reports.
