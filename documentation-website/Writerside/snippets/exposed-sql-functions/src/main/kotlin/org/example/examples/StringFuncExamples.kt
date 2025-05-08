package org.example.examples

import org.example.tables.FilmBoxOfficeTable
import org.jetbrains.exposed.v1.sql.Concat
import org.jetbrains.exposed.v1.sql.SqlExpressionBuilder.concat
import org.jetbrains.exposed.v1.sql.alias
import org.jetbrains.exposed.v1.sql.charLength
import org.jetbrains.exposed.v1.sql.insert
import org.jetbrains.exposed.v1.sql.locate
import org.jetbrains.exposed.v1.sql.lowerCase
import org.jetbrains.exposed.v1.sql.stringLiteral
import org.jetbrains.exposed.v1.sql.substring
import org.jetbrains.exposed.v1.sql.trim
import org.jetbrains.exposed.v1.sql.upperCase

/*
    Important: This file is referenced by line number in `SQL-Functions.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

private const val REVENUE_MONTH = 1
private const val REVENUE_YEAR = 2019

class StringFuncExamples {
    fun selectStringFunctions() {
        FilmBoxOfficeTable.insert {
            it[title] = "The Rise of Skywalker"
            it[region] = "Netherlands"
            it[revenue] = 99.toBigDecimal()
            it[month] = REVENUE_MONTH
            it[year] = REVENUE_YEAR
        }

        val lowerCaseTitle = FilmBoxOfficeTable.title.lowerCase()
        val lowerCaseTitles = FilmBoxOfficeTable.select(lowerCaseTitle).map { it[lowerCaseTitle] }
        println(lowerCaseTitles)

        val upperCaseRegion = FilmBoxOfficeTable.region.upperCase().alias("reg_all_caps")
        val upperCaseRegions = FilmBoxOfficeTable.select(upperCaseRegion).map { it[upperCaseRegion] }
        println(upperCaseRegions)

        val fullFilmTitle = Concat(separator = " ", FilmBoxOfficeTable.region, stringLiteral("||"), FilmBoxOfficeTable.title)
            .trim()
            .lowerCase()
        val fullFilmTitles = FilmBoxOfficeTable.select(fullFilmTitle).map { it[fullFilmTitle] }
        println(fullFilmTitles)

        val shortenedTitle = FilmBoxOfficeTable.title.substring(start = 1, length = 3)
        val shortenedTitles = FilmBoxOfficeTable.select(shortenedTitle).map { it[shortenedTitle] }
        println(shortenedTitles)

        val filmTitle = concat(
            separator = " - ",
            expr = listOf(stringLiteral("Title"), FilmBoxOfficeTable.title)
        )
        val filmTitles = FilmBoxOfficeTable.select(filmTitle).map { it[filmTitle] }
        println(filmTitles)

        val firstXSIndex = FilmBoxOfficeTable.title.locate("XS")
        val firstXSIndices = FilmBoxOfficeTable.select(firstXSIndex).map { it[firstXSIndex] }
        println(firstXSIndices)

        val titleLength = FilmBoxOfficeTable.title.charLength()
        val titleLengths = FilmBoxOfficeTable.select(titleLength).map { it[titleLength] }
        println(titleLengths)
    }
}
