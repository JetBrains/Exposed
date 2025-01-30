package org.example.examples

import org.example.tables.FilmBoxOfficeTable
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.stdDevPop
import org.jetbrains.exposed.sql.sum

/*
    Important: This file is referenced by line number in `SQL-Functions.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

class AggregateFuncExamples {
    fun selectAggregateFunctions() {
        val minRevenue = FilmBoxOfficeTable.revenue.min()
        val maxRevenue = FilmBoxOfficeTable.revenue.max()
        val averageRevenue = FilmBoxOfficeTable.revenue.avg()
        val revenueStats = FilmBoxOfficeTable
            .select(minRevenue, maxRevenue, averageRevenue, FilmBoxOfficeTable.region)
            .groupBy(FilmBoxOfficeTable.region)
            .map {
                Triple(it[minRevenue], it[maxRevenue], it[averageRevenue])
            }
        println(revenueStats)

        val revenueSum = FilmBoxOfficeTable.revenue.sum()
        val revenueCount = FilmBoxOfficeTable.revenue.count()
        val revenueReport = FilmBoxOfficeTable
            .select(revenueSum, revenueCount, FilmBoxOfficeTable.region)
            .groupBy(FilmBoxOfficeTable.region)
            .map {
                it[revenueSum] to it[revenueCount]
            }
        println(revenueReport)

        val revenueStdDev = FilmBoxOfficeTable.revenue.stdDevPop()
        val stdDev = FilmBoxOfficeTable
            .select(revenueStdDev)
            .singleOrNull()
            ?.get(revenueStdDev)
        println(stdDev)
    }
}
