package org.example.kotlinai.eval

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvalMetricsTest {

    private fun approx(expected: Double, actual: Double, eps: Double = 1e-9) {
        assertTrue(abs(expected - actual) < eps, "expected=$expected actual=$actual")
    }

    @Test
    fun `recallAtK returns hits over relevant size`() {
        val returned = listOf(1L, 2L, 3L, 4L, 5L)
        val relevant = setOf(2L, 5L, 99L)
        assertEquals(2.0 / 3.0, EvalMetrics.recallAtK(returned, relevant, 10))
    }

    @Test
    fun `recallAtK respects K truncation`() {
        val returned = listOf(1L, 2L, 3L, 4L, 5L)
        val relevant = setOf(4L, 5L)
        assertEquals(0.0, EvalMetrics.recallAtK(returned, relevant, 3))
        assertEquals(1.0, EvalMetrics.recallAtK(returned, relevant, 5))
    }

    @Test
    fun `recallAtK returns NaN on empty relevant`() {
        assertTrue(EvalMetrics.recallAtK(listOf(1L), emptySet(), 10).isNaN())
    }

    @Test
    fun `precisionAtK divides hits by K`() {
        val returned = listOf(1L, 2L, 3L, 4L)
        val relevant = setOf(1L, 3L)
        assertEquals(2.0 / 4.0, EvalMetrics.precisionAtK(returned, relevant, 4))
        assertEquals(1.0 / 2.0, EvalMetrics.precisionAtK(returned, relevant, 2))
    }

    @Test
    fun `ndcgAtK perfect order is 1`() {
        val returned = listOf(10L, 20L, 30L)
        val relevant = setOf(10L, 20L, 30L)
        approx(1.0, EvalMetrics.ndcgAtK(returned, relevant, 3))
    }

    @Test
    fun `ndcgAtK penalizes wrong order`() {
        val perfect = EvalMetrics.ndcgAtK(listOf(1L, 2L, 3L), setOf(1L, 2L), 3)
        val shuffled = EvalMetrics.ndcgAtK(listOf(3L, 1L, 2L), setOf(1L, 2L), 3)
        assertTrue(perfect > shuffled, "perfect=$perfect shuffled=$shuffled")
    }

    @Test
    fun `ndcgAtK returns 0 when no hits`() {
        assertEquals(0.0, EvalMetrics.ndcgAtK(listOf(1L, 2L), setOf(99L), 10))
    }

    @Test
    fun `mrr uses first hit rank`() {
        assertEquals(1.0, EvalMetrics.mrr(listOf(5L, 2L), setOf(5L)))
        assertEquals(1.0 / 3.0, EvalMetrics.mrr(listOf(1L, 2L, 3L), setOf(3L)))
    }

    @Test
    fun `mrr returns 0 when no hit`() {
        assertEquals(0.0, EvalMetrics.mrr(listOf(1L, 2L), setOf(99L)))
    }
}