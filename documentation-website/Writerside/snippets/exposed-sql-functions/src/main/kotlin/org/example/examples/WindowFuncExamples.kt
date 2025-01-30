package org.example.examples

import org.example.tables.SalesTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.rowNumber
import org.jetbrains.exposed.sql.WindowFrameBound
import org.jetbrains.exposed.sql.sum

/*
    Important: This file is referenced by line number in `SQL-Functions.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

class WindowFuncExamples {
    fun selectWindowFunctions() {
        val window1 = SalesTable.amount.sum()
            .over()
            .partitionBy(SalesTable.year, SalesTable.product)
            .orderBy(SalesTable.amount)
        val result1 = SalesTable.select(window1).map { it[window1] }
        println(result1)

        val window2 = rowNumber()
            .over()
            .partitionBy(SalesTable.year, SalesTable.product)
            .orderBy(SalesTable.amount)
        val result2 = SalesTable.select(window2).map { it[window2] }
        println(result2)

        val window3 = SalesTable.amount.sum()
            .over()
            .orderBy(SalesTable.year to SortOrder.DESC, SalesTable.product to SortOrder.ASC_NULLS_FIRST)
        val result3 = SalesTable.select(window3).map { it[window3] }
        println(result3)

        val window4 = SalesTable.amount.sum()
            .over()
            .partitionBy(SalesTable.year, SalesTable.product)
            .orderBy(SalesTable.amount)
            .range(WindowFrameBound.offsetPreceding(2), WindowFrameBound.currentRow())
        val result4 = SalesTable.select(window4).map { it[window4] }
        println(result4)
    }
}
