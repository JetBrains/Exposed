package org.jetbrains.exposed.v1.perf

import kotlinx.coroutines.runBlocking

const val WARMUP_OPS = 1_000
const val MEASURE_ITERATIONS = 10
const val MEASURE_OPS = 10_000

data class BackendResult(val backend: String, val medianNsPerOp: Long)

data class ScenarioResult(val scenario: String, val results: List<BackendResult>)

fun benchSync(name: String, op: () -> Unit): Long {
    repeat(WARMUP_OPS) { op() }

    val timings = LongArray(MEASURE_ITERATIONS)
    for (i in 0 until MEASURE_ITERATIONS) {
        val start = System.nanoTime()
        repeat(MEASURE_OPS) { op() }
        timings[i] = (System.nanoTime() - start) / MEASURE_OPS
    }
    timings.sort()
    return timings[MEASURE_ITERATIONS / 2]
}

fun benchSuspend(name: String, op: suspend () -> Unit): Long = runBlocking {
    repeat(WARMUP_OPS) { op() }

    val timings = LongArray(MEASURE_ITERATIONS)
    for (i in 0 until MEASURE_ITERATIONS) {
        val start = System.nanoTime()
        repeat(MEASURE_OPS) { op() }
        timings[i] = (System.nanoTime() - start) / MEASURE_OPS
    }
    timings.sort()
    timings[MEASURE_ITERATIONS / 2]
}

fun runScenario(scenario: Scenario): ScenarioResult {
    return ScenarioResult(
        scenario = scenario.name,
        results = listOf(
            BackendResult("Raw JDBC", benchSync(scenario.name + "/raw-jdbc", scenario.rawJdbc)),
            BackendResult("Exposed JDBC", benchSync(scenario.name + "/exposed-jdbc", scenario.exposedJdbc)),
            BackendResult("Raw R2DBC", benchSuspend(scenario.name + "/raw-r2dbc", scenario.rawR2dbc)),
            BackendResult("Exposed R2DBC", benchSuspend(scenario.name + "/exposed-r2dbc", scenario.exposedR2dbc)),
        )
    )
}
