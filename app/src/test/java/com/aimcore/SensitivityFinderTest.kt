package com.aimcore

/** Dependency-free smoke test. Invoke runAll() from a local Kotlin test harness. */
object SensitivityFinderTest {
    fun runAll() {
        val strong = RoundMetrics(
            accuracyPercent = 90.0,
            hits = 18,
            shots = 20,
            timeToTargetMs = 700,
            overshoots = 1,
            undershoots = 1,
            pathEfficiency = 0.85,
            timeOnTargetPercent = 75.0
        )
        val weak = strong.copy(accuracyPercent = 30.0, timeToTargetMs = 4200, pathEfficiency = 0.25)
        check(FinderScoring.score(strong) > FinderScoring.score(weak))

        val search = SensitivitySearch(0.15, seed = 1)
        var round = search.nextRound()
        var safety = 0
        while (round != null && !search.isComplete && safety < 40) {
            search.record(round, strong)
            round = search.nextRound()
            safety++
        }
        check(search.isComplete)
        val result = search.result()
        check(result != null)
        check(result.recommendedSensitivity > 0.0)
        check(result.points.isNotEmpty())
    }
}

fun main() { SensitivityFinderTest.runAll() }
