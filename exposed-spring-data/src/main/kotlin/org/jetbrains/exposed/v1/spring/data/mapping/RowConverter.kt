package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow

internal interface RowConverter {
    val columns: List<Column<*>>

    fun convert(row: ResultRow): Any
}
