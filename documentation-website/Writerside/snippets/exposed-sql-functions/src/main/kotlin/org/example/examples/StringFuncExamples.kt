package org.example.examples

import org.example.tables.SalesTable
import org.jetbrains.exposed.sql.Concat
import org.jetbrains.exposed.sql.SqlExpressionBuilder.concat
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.charLength
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.locate
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.substring
import org.jetbrains.exposed.sql.trim
import org.jetbrains.exposed.sql.upperCase

/*
    Important: This file is referenced by line number in `SQL-Functions.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

private const val SALES_MONTH = 1
private const val SALES_YEAR = 2025

class StringFuncExamples {
    fun selectStringFunctions() {
        SalesTable.insert {
            it[label] = "Label A"
            it[product] = "Product A"
            it[amount] = 99.toBigDecimal()
            it[month] = SALES_MONTH
            it[year] = SALES_YEAR
        }

        val lowerCaseLabel = SalesTable.label.lowerCase()
        val lowerCaseLabels = SalesTable.select(lowerCaseLabel).map { it[lowerCaseLabel] }
        println(lowerCaseLabels)

        val upperCaseProduct = SalesTable.product.upperCase().alias("prd_all_caps")
        val upperCaseProducts = SalesTable.select(upperCaseProduct).map { it[upperCaseProduct] }
        println(upperCaseProducts)

        val fullProductLabel = Concat(separator = " ", SalesTable.product, stringLiteral("||"), SalesTable.label)
            .trim()
            .lowerCase()
        val fullProductLabels = SalesTable.select(fullProductLabel).map { it[fullProductLabel] }
        println(fullProductLabels)

        val shortenedLabel = SalesTable.label.substring(start = 1, length = 3)
        val shortenedLabels = SalesTable.select(shortenedLabel).map { it[shortenedLabel] }
        println(shortenedLabels)

        val productName = concat(
            separator = " - ",
            expr = listOf(stringLiteral("Product"), SalesTable.product)
        )
        val productNames = SalesTable.select(productName).map { it[productName] }
        println(productNames)

        val firstXSIndex = SalesTable.label.locate("XS")
        val firstXSIndices = SalesTable.select(firstXSIndex).map { it[firstXSIndex] }
        println(firstXSIndices)

        val labelLength = SalesTable.label.charLength()
        val labelLengths = SalesTable.select(labelLength).map { it[labelLength] }
        println(labelLengths)
    }
}
