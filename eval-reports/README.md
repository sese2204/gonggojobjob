# Search Quality Eval Reports

Rolling record of retrieval-quality experiments on the Activity hybrid search path
(`ActivityHybridSearchService` + pgvector + pg_trgm).

## Layout

- `runs/` — raw JSON reports emitted by `SearchEvalTest` (one per `EVAL_LABEL` run).
  Source copy lives under `src/test/resources/eval/results/`; this folder is the
  human-readable archive kept at the repo root.
- `README.md` (this file) — current metrics table + changelog.

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

| Label | Date | Commit | Corpus snapshot | Notes |
|---|---|---|---|---|
| `baseline` | 2026-04-18 | 75eb6a7 | 2026-04-18 | First labeled run. Baseline = AND-joined `to_tsquery` + `ts_rank` + ILIKE fallback. |
| `phase1` | 2026-04-20 | 75eb6a7 | 2026-04-20 | OR+prefix `to_tsquery` + `ts_rank_cd`, no ILIKE fallback. Looked like regression — turned out to be corpus drift. |
| `baseline-2` | 2026-04-20 | 75eb6a7 | 2026-04-20 | Baseline code re-measured on current corpus with v2 labels. This is the fair comparison point for `phase1`. |
| `phase1-2` | 2026-04-20 | 75eb6a7 | 2026-04-20 | Phase 1 code re-measured with v2 labels. Fair comparison vs `baseline-2`. |
| `tune-kw60` | 2026-04-20 | 75eb6a7 | 2026-04-20 | Phase 1 + RRF weights (kw=0.6, vec=0.4). Keyword-heavy. |
| `tune-kw40` | 2026-04-20 | 75eb6a7 | 2026-04-20 | Phase 1 + RRF weights (kw=0.4, vec=0.6). Vector-heavy. |

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
