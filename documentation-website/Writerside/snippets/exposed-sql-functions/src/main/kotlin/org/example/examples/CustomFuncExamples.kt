package org.example.examples

import org.example.tables.FilmBoxOfficeTable
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

private const val REVENUE_MONTH = 1
private const val REVENUE_YEAR = 1999

class CustomFuncExamples {
    fun selectCustomFunctions() {
        val sqrtAmount = FilmBoxOfficeTable.revenue.function("SQRT")
        // generates SQL: SQRT(SALES.AMOUNT)
        val sqrt = FilmBoxOfficeTable
            .select(sqrtAmount)
            .singleOrNull()
            ?.get(sqrtAmount)
        println(sqrt)

        val replacedTitle = CustomFunction(
            functionName = "REPLACE",
            columnType = TextColumnType(),
            FilmBoxOfficeTable.title, stringLiteral("Title"), stringLiteral("New Title")
        )
        // generates SQL: REPLACE(FILMBOXOFFICE.TITLE, 'Title', 'New Title')
        val replacedTitles = FilmBoxOfficeTable.select(replacedTitle).map { it[replacedTitle] }
        println(replacedTitles)

        val replacedStringTitle = CustomStringFunction(
            "REPLACE", FilmBoxOfficeTable.title, stringLiteral("Title"), stringLiteral("New Title")
        )
        val replacedStringTitles = FilmBoxOfficeTable.select(replacedStringTitle).map { it[replacedStringTitle] }
        println(replacedStringTitles)
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
        val revenueInLast3Months = FilmBoxOfficeTable
            .selectAll()
            .where { FilmBoxOfficeTable.month greater threeMonthsAgo }
            .map { it[FilmBoxOfficeTable.title] }
        println(revenueInLast3Months)
    }

    fun selectCustomTrimFunction() {
        FilmBoxOfficeTable.deleteAll()

        FilmBoxOfficeTable.insert {
            it[title] = "Star Wars: The Phantom Menace - Episode I"
            it[region] = "Spain"
            it[revenue] = 99.toBigDecimal()
            it[month] = REVENUE_MONTH
            it[year] = REVENUE_YEAR
        }

        val leadingStarWarsTrim = FilmBoxOfficeTable.title.customTrim(stringLiteral("Star Wars:"), TrimSpecifier.LEADING)
        val titleWithoutPrefix = FilmBoxOfficeTable.select(leadingStarWarsTrim).single()[leadingStarWarsTrim] // Episode I - The Phantom Menace
        println(titleWithoutPrefix)

        val trailingEpisodeTrim = FilmBoxOfficeTable.title.customTrim(stringLiteral("- Episode I"), TrimSpecifier.TRAILING)
        val titleWithoutSuffix = FilmBoxOfficeTable.select(trailingEpisodeTrim).single()[trailingEpisodeTrim] // Star Wars: The Phantom Menace
        println(titleWithoutSuffix)
    }
}
