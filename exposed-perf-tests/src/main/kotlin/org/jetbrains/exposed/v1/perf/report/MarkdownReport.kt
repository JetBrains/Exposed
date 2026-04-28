package org.jetbrains.exposed.v1.perf.report

import org.jetbrains.exposed.v1.perf.ScenarioResult

fun formatScenarioTable(results: List<ScenarioResult>): String {
    val backends = listOf("Raw JDBC", "Exposed JDBC", "Raw R2DBC", "Exposed R2DBC")
    val sb = StringBuilder()

    sb.appendLine("### Per-scenario timings (ns/op, median over 10 iterations of 10000 ops)")
    sb.appendLine()
    sb.append("| Scenario              ")
    for (b in backends) sb.append("| ").append(b.padEnd(13)).append(" ")
    sb.appendLine("|")
    sb.append("|-----------------------")
    repeat(backends.size) { sb.append("|--------------:") }
    sb.appendLine("|")

    for (sr in results) {
        sb.append("| ").append(sr.scenario.padEnd(22, ' ')).append(" ")
        for (backend in backends) {
            val ns = sr.results.find { it.backend == backend }?.medianNsPerOp ?: -1L
            sb.append("| ").append(ns.toString().padStart(13)).append(" ")
        }
        sb.appendLine("|")
    }
    return sb.toString()
}

fun formatRatioTable(results: List<ScenarioResult>): String {
    val sb = StringBuilder()
    sb.appendLine("### Ratios")
    sb.appendLine()
    sb.appendLine("| Scenario              | Exposed-R2DBC vs Raw-R2DBC | Exposed-R2DBC vs Exposed-JDBC |")
    sb.appendLine("|-----------------------|---------------------------:|------------------------------:|")
    for (sr in results) {
        val rawR2 = sr.results.first { it.backend == "Raw R2DBC" }.medianNsPerOp.toDouble()
        val expR2 = sr.results.first { it.backend == "Exposed R2DBC" }.medianNsPerOp.toDouble()
        val expJ = sr.results.first { it.backend == "Exposed JDBC" }.medianNsPerOp.toDouble()
        val ratio1 = "%.2fx".format(expR2 / rawR2)
        val ratio2 = "%.2fx".format(expR2 / expJ)
        sb.append("| ").append(sr.scenario.padEnd(22, ' ')).append(" ")
        sb.append("| ").append(ratio1.padStart(26)).append(" ")
        sb.append("| ").append(ratio2.padStart(29)).append(" ")
        sb.appendLine("|")
    }
    return sb.toString()
}
