package org.example.examples

import org.example.tables.SalesTable
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
        val minAmount = SalesTable.amount.min()
        val maxAmount = SalesTable.amount.max()
        val averageAmount = SalesTable.amount.avg()
        val amountStats = SalesTable
            .select(minAmount, maxAmount, averageAmount, SalesTable.label)
            .groupBy(SalesTable.label)
            .map {
                Triple(it[minAmount], it[maxAmount], it[averageAmount])
            }
        println(amountStats)

        val amountSum = SalesTable.amount.sum()
        val amountCount = SalesTable.amount.count()
        val amountReport = SalesTable
            .select(amountSum, amountCount, SalesTable.label)
            .groupBy(SalesTable.label)
            .map {
                it[amountSum] to it[amountCount]
            }
        println(amountReport)

        val amountStdDev = SalesTable.amount.stdDevPop()
        val stdDev = SalesTable
            .select(amountStdDev)
            .singleOrNull()
            ?.get(amountStdDev)
        println(stdDev)
    }
}
