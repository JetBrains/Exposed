package org.example.examples

import org.example.tables.SalesTable
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.CustomStringFunction
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.function
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDate
import org.jetbrains.exposed.sql.kotlin.datetime.CustomDateFunction
import org.jetbrains.exposed.sql.kotlin.datetime.month
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral

/*
    Important: This file is referenced by line number in `SQL-Functions.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

private const val SALES_MONTH = 1
private const val SALES_YEAR = 2025

class CustomFuncExamples {
    fun selectCustomFunctions() {
        val sqrtAmount = SalesTable.amount.function("SQRT")
        // generates SQL: SQRT(SALES.AMOUNT)
        val sqrt = SalesTable
            .select(sqrtAmount)
            .singleOrNull()
            ?.get(sqrtAmount)
        println(sqrt)

        val replacedLabel = CustomFunction(
            functionName = "REPLACE",
            columnType = TextColumnType(),
            SalesTable.label, stringLiteral("Label"), stringLiteral("New Label")
        )
        // generates SQL: REPLACE(SALES.LABEL, 'Label', 'New Label')
        val replacedLabels = SalesTable.select(replacedLabel).map { it[replacedLabel] }
        println(replacedLabels)

        val replacedStringLabel = CustomStringFunction(
            "REPLACE", SalesTable.label, stringLiteral("Label"), stringLiteral("New Label")
        )
        val replacedStringLabels = SalesTable.select(replacedStringLabel).map { it[replacedStringLabel] }
        println(replacedStringLabels)
    }

    @Suppress("MagicNumber")
    fun selectCustomDateFunction() {
        val threeMonthsAgo = CustomDateFunction(
            functionName = "DATEADD",
            stringLiteral("MONTH"),
            intLiteral(-3),
            CurrentDate
        ).month()
        // generates SQL: MONTH(DATEADD('MONTH', -3, CURRENT_DATE))
        val salesInLast3Months = SalesTable
            .selectAll()
            .where { SalesTable.month greater threeMonthsAgo }
            .map { it[SalesTable.product] }
        println(salesInLast3Months)
    }

    fun selectCustomTrimFunction() {
        SalesTable.deleteAll()

        SalesTable.insert {
            it[label] = "xxxxLabelxxxx"
            it[product] = "Product"
            it[amount] = 99.toBigDecimal()
            it[month] = SALES_MONTH
            it[year] = SALES_YEAR
        }

        val leadingXTrim = SalesTable.label.customTrim(stringLiteral("x"), TrimSpecifier.LEADING)
        val labelWithoutPrefix = SalesTable.select(leadingXTrim).single()[leadingXTrim] // Labelxxxx
        println(labelWithoutPrefix)

        val trailingXTrim = SalesTable.label.customTrim(stringLiteral("x"), TrimSpecifier.TRAILING)
        val labelWithoutSuffix = SalesTable.select(trailingXTrim).single()[trailingXTrim] // xxxxLabel
        println(labelWithoutSuffix)
    }
}
