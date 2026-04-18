package org.example.kotlinai.eval

import kotlin.math.log2

object EvalMetrics {

    fun recallAtK(returned: List<Long>, relevant: Set<Long>, k: Int): Double {
        if (relevant.isEmpty()) return Double.NaN
        val hits = returned.take(k).count { it in relevant }
        return hits.toDouble() / relevant.size
    }

    fun precisionAtK(returned: List<Long>, relevant: Set<Long>, k: Int): Double {
        if (k <= 0) return Double.NaN
        val hits = returned.take(k).count { it in relevant }
        return hits.toDouble() / k
    }

    fun ndcgAtK(returned: List<Long>, relevant: Set<Long>, k: Int): Double {
        if (relevant.isEmpty()) return Double.NaN
        val dcg = returned.take(k).mapIndexed { idx, id ->
            if (id in relevant) 1.0 / log2((idx + 2).toDouble()) else 0.0
        }.sum()
        val idealHits = minOf(relevant.size, k)
        val idcg = (0 until idealHits).sumOf { idx -> 1.0 / log2((idx + 2).toDouble()) }
        return if (idcg == 0.0) 0.0 else dcg / idcg
    }

    fun mrr(returned: List<Long>, relevant: Set<Long>): Double {
        if (relevant.isEmpty()) return Double.NaN
        val rank = returned.indexOfFirst { it in relevant }
        return if (rank < 0) 0.0 else 1.0 / (rank + 1)
    }
}