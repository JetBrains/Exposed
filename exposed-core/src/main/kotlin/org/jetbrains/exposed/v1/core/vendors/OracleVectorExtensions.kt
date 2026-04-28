package org.jetbrains.exposed.v1.core.vendors

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table

fun Table.vector(name: String, dimensions: Int): Column<FloatArray> =
    registerColumn(name, OracleVectorColumnType(dimensions))
