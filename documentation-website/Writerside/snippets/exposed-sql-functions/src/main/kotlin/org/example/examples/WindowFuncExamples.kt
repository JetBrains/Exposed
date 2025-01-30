package org.example.examples

import org.example.tables.FilmBoxOfficeTable
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
        val window1 = FilmBoxOfficeTable.revenue.sum()
            .over()
            .partitionBy(FilmBoxOfficeTable.year, FilmBoxOfficeTable.title)
            .orderBy(FilmBoxOfficeTable.revenue)
        val result1 = FilmBoxOfficeTable.select(window1).map { it[window1] }
        println(result1)

        val window2 = rowNumber()
            .over()
            .partitionBy(FilmBoxOfficeTable.year, FilmBoxOfficeTable.title)
            .orderBy(FilmBoxOfficeTable.revenue)
        val result2 = FilmBoxOfficeTable.select(window2).map { it[window2] }
        println(result2)

        val window3 = FilmBoxOfficeTable.revenue.sum()
            .over()
            .orderBy(FilmBoxOfficeTable.year to SortOrder.DESC, FilmBoxOfficeTable.title to SortOrder.ASC_NULLS_FIRST)
        val result3 = FilmBoxOfficeTable.select(window3).map { it[window3] }
        println(result3)

        val window4 = FilmBoxOfficeTable.revenue.sum()
            .over()
            .partitionBy(FilmBoxOfficeTable.year, FilmBoxOfficeTable.title)
            .orderBy(FilmBoxOfficeTable.revenue)
            .range(WindowFrameBound.offsetPreceding(2), WindowFrameBound.currentRow())
        val result4 = FilmBoxOfficeTable.select(window4).map { it[window4] }
        println(result4)
    }
}
