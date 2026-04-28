package org.jetbrains.exposed.v1.perf

import org.jetbrains.exposed.v1.perf.report.formatRatioTable
import org.jetbrains.exposed.v1.perf.report.formatScenarioTable
import org.jetbrains.exposed.v1.perf.scenarios.insertBatchScenario
import org.jetbrains.exposed.v1.perf.scenarios.insertSingleScenario
import org.jetbrains.exposed.v1.perf.scenarios.selectByPkScenario
import org.jetbrains.exposed.v1.perf.scenarios.selectManyScenario
import org.jetbrains.exposed.v1.perf.scenarios.updateSingleScenario

fun main() {
    setUpSchemaAndSeedData()

    val scenarios = listOf(
        selectByPkScenario(),
        selectManyScenario(rowCount = 100),
        selectManyScenario(rowCount = 1000),
        insertSingleScenario(),
        insertBatchScenario(),
        updateSingleScenario(),
    )

    val results = scenarios.map(::runScenario)

    for (r in results) {
        println("=== ${r.scenario} ===")
        for (br in r.results) println("  ${br.backend}: ${br.medianNsPerOp} ns/op")
    }

    println()
    println("## Baseline benchmark — H2 in-memory")
    println()
    println(formatScenarioTable(results))
    println(formatRatioTable(results))
}
