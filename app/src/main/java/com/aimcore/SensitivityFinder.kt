package com.aimcore

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Pure Kotlin sensitivity search and scoring. No Android dependencies. */
data class RoundMetrics(
    val accuracyPercent: Double,
    val hits: Int,
    val shots: Int,
    val timeToTargetMs: Long,
    val overshoots: Int,
    val undershoots: Int,
    val pathEfficiency: Double,
    val timeOnTargetPercent: Double
)

data class FinderRound(val sensitivityDegPerDp: Double, val phase: String, val index: Int, val scenario: String)
data class SensitivityPoint(val sensitivityDegPerDp: Double, val score: Double, val rounds: Int)
data class FinderResult(
    val recommendedSensitivity: Double,
    val score: Double,
    val confidence: String,
    val tendency: String,
    val points: List<SensitivityPoint>,
    val metrics: RoundMetrics
)

object FinderScoring {
    fun score(metrics: RoundMetrics): Double {
        val accuracy = clamp(metrics.accuracyPercent / 100.0)
        val speed = 1.0 - clamp(metrics.timeToTargetMs.toDouble() / 5000.0)
        val efficiency = clamp(metrics.pathEfficiency)
        val correctionCount = metrics.overshoots + metrics.undershoots
        val correction = if (metrics.shots <= 0) 0.0 else 1.0 - clamp(correctionCount.toDouble() / metrics.shots)
        val tracking = clamp(metrics.timeOnTargetPercent / 100.0)
        return (accuracy * 0.30 + speed * 0.15 + efficiency * 0.20 + correction * 0.10 + tracking * 0.25) * 100.0
    }

    private fun clamp(value: Double): Double = max(0.0, min(1.0, value))
}

/** Coarse log sweep -> refine around top two -> three confirmation repeats each. */
class SensitivitySearch(
    private val startingSensitivity: Double,
    seed: Int = Random.nextInt()
) {
    private val random = Random(seed)
    private val scores = linkedMapOf<Double, MutableList<Double>>()
    private val metricSamples = linkedMapOf<Double, MutableList<RoundMetrics>>()
    private var queue = mutableListOf<FinderRound>()
    private var phase = "COARSE"
    private var roundIndex = 0
    private var started = false
    var isComplete: Boolean = false
        private set

    init { require(startingSensitivity > 0.0) }

    fun nextRound(): FinderRound? {
        if (!started) {
            started = true
            queue = logSweep(startingSensitivity, 7, 0.62, 1.62).shuffled(random)
                .map { FinderRound(it, phase, ++roundIndex, scenarioFor(roundIndex)) }.toMutableList()
        }
        if (queue.isEmpty()) return null
        return queue.removeAt(0)
    }

    fun record(round: FinderRound, metrics: RoundMetrics) {
        val key = round.sensitivityDegPerDp
        scores.getOrPut(key) { mutableListOf() }.add(FinderScoring.score(metrics))
        metricSamples.getOrPut(key) { mutableListOf() }.add(metrics)
        if (queue.isEmpty()) advancePhase(round.phase)
    }

    private fun advancePhase(completedPhase: String) {
        if (completedPhase == "COARSE" && phase == "COARSE") {
            phase = "REFINE"
            val top = ranked().take(2).map { it.sensitivityDegPerDp }
            val candidates = top.flatMap { center -> listOf(center * 0.86, center, center * 1.14) }
                .map { it.coerceIn(startingSensitivity * 0.25, startingSensitivity * 4.0) }
                .distinctBy { (ln(it) * 10000).toLong() }
            queue = candidates.shuffled(random).map { FinderRound(it, phase, ++roundIndex, scenarioFor(roundIndex)) }.toMutableList()
            if (queue.isEmpty()) advancePhase("REFINE")
        } else if (completedPhase == "REFINE" && phase == "REFINE") {
            phase = "CONFIRM"
            val top = ranked().take(2).map { it.sensitivityDegPerDp }
            queue = top.flatMap { value -> List(3) { value } }.shuffled(random)
                .map { FinderRound(it, phase, ++roundIndex, scenarioFor(roundIndex)) }.toMutableList()
            if (queue.isEmpty()) isComplete = true
        } else if (completedPhase == "CONFIRM" && phase == "CONFIRM") {
            isComplete = true
        }
    }

    fun result(): FinderResult? {
        if (!isComplete || scores.isEmpty()) return null
        val ranked = ranked()
        val best = ranked.first()
        val bestMetrics = aggregateMetrics(metricSamples[best.sensitivityDegPerDp].orEmpty())
        val gap = if (ranked.size > 1) best.score - ranked[1].score else 0.0
        val repeats = best.rounds
        val confidence = when {
            repeats >= 3 && gap >= 7.0 -> "HIGH"
            repeats >= 2 && gap >= 3.0 -> "MEDIUM"
            else -> "LOW"
        }
        val tendency = when {
            bestMetrics.overshoots > bestMetrics.undershoots + 1 -> "TOO FAST"
            bestMetrics.undershoots > bestMetrics.overshoots + 1 -> "TOO SLOW"
            else -> "BALANCED"
        }
        return FinderResult(best.sensitivityDegPerDp, best.score, confidence, tendency, ranked.sortedBy { it.sensitivityDegPerDp }, bestMetrics)
    }

    fun restAfterRoundMs(): Long = 1500L

    private fun scenarioFor(index: Int): String = when ((index - 1) % 4) {
        0 -> "STATIC FLICK"
        1 -> "MOVING TRACKING"
        2 -> "LONG-RANGE MICRO-ADJUST"
        else -> "TARGET SWITCHING"
    }

    private fun ranked(): List<SensitivityPoint> = scores.map { (sensitivity, values) ->
        SensitivityPoint(sensitivity, median(values), values.size)
    }.sortedByDescending { it.score }

    private fun logSweep(center: Double, count: Int, low: Double, high: Double): List<Double> {
        if (count <= 1) return listOf(center)
        val minLog = ln(center * low)
        val maxLog = ln(center * high)
        return (0 until count).map { index ->
            exp(minLog + (maxLog - minLog) * index / (count - 1).toDouble())
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun aggregateMetrics(values: List<RoundMetrics>): RoundMetrics {
        if (values.isEmpty()) return RoundMetrics(0.0, 0, 0, 0, 0, 0, 0.0, 0.0)
        fun med(selector: (RoundMetrics) -> Double): Double = median(values.map(selector))
        return RoundMetrics(
            med { it.accuracyPercent },
            values.sumOf { it.hits } / values.size,
            values.sumOf { it.shots } / values.size,
            med { it.timeToTargetMs.toDouble() }.toLong(),
            values.sumOf { it.overshoots } / values.size,
            values.sumOf { it.undershoots } / values.size,
            med { it.pathEfficiency },
            med { it.timeOnTargetPercent }
        )
    }
}
