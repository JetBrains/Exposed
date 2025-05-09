package org.example.examples

import org.example.tables.FilmBoxOfficeTable
import org.jetbrains.exposed.v1.avg
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.stdDevPop
import org.jetbrains.exposed.v1.max
import org.jetbrains.exposed.v1.min
import org.jetbrains.exposed.v1.sum

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
