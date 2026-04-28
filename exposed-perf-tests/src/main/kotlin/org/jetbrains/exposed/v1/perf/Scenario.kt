package org.jetbrains.exposed.v1.perf

/**
 * One scenario produces 4 named "backend" runners. Each runner does one unit of work
 * (e.g. one SELECT) and asserts correctness internally.
 */
data class Scenario(
    val name: String,
    val rawJdbc: () -> Unit,
    val exposedJdbc: () -> Unit,
    val rawR2dbc: suspend () -> Unit,
    val exposedR2dbc: suspend () -> Unit,
)
