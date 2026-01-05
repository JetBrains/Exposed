package org.example.examples

import org.example.tables.ActorsTable
import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.anyFrom
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.notLike
import org.jetbrains.exposed.v1.core.regexp
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/*
    Important: All assigned examples in this file are referenced by name and all unassigned examples by line number in `DSL-Querying-Data.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers and/or names in the `code-block` element of the referenced file.
 */

private const val MOVIE_ORIGINAL_ID = 4
private const val MOVIE_SEQUEL_ID = 5
private const val MOVIE_SEQUEL_2_ID = 6
private const val MOVIE_SEQUEL_3_ID = 8

class QueryingExamples {

    fun useWhereConditions() {
        val allMoviesLike = StarWarsFilmsTable.selectAll()
            .where { StarWarsFilmsTable.name like "The %" }
        println(allMoviesLike.toList())

        val allMoviesNotLike = StarWarsFilmsTable.selectAll()
            .where { StarWarsFilmsTable.name notLike "The %" }
        println(allMoviesNotLike.toList())

        val allMatchingRegex = StarWarsFilmsTable.selectAll()
            .where { StarWarsFilmsTable.name regexp "^The(\\s\\w+){2}\$" }
        println(allMatchingRegex.toList())

        val allBetween = StarWarsFilmsTable.selectAll()
            .where { StarWarsFilmsTable.sequelId.between(MOVIE_ORIGINAL_ID, MOVIE_SEQUEL_2_ID) }
        println(allBetween.toList())

        val allInList = StarWarsFilmsTable.selectAll()
            .where { StarWarsFilmsTable.sequelId inList listOf(MOVIE_SEQUEL_2_ID, MOVIE_ORIGINAL_ID) }
        println(allInList.toList())

        val topRated = listOf(MOVIE_SEQUEL_ID to "Empire Strikes Back", MOVIE_ORIGINAL_ID to "A New Hope")
        val multipleInList = StarWarsFilmsTable.selectAll()
            .where {
                StarWarsFilmsTable.sequelId to StarWarsFilmsTable.name inList topRated
            }
        println(multipleInList.toList())

        val anyFromArray = StarWarsFilmsTable.selectAll()
            .where {
                StarWarsFilmsTable.sequelId eq anyFrom(arrayOf(MOVIE_SEQUEL_2_ID, MOVIE_ORIGINAL_ID))
            }
        println(anyFromArray.toList())
    }

    fun aggregateAndSort() {
        val count = StarWarsFilmsTable.selectAll()
            .where {
                StarWarsFilmsTable.sequelId eq MOVIE_SEQUEL_3_ID
            }
            .count()
        println(count)

        val sortedFilms = StarWarsFilmsTable.selectAll()
            .orderBy(StarWarsFilmsTable.sequelId to SortOrder.ASC)
        println((sortedFilms).toList())

        val groupedFilms = StarWarsFilmsTable
            .select(StarWarsFilmsTable.sequelId.count(), StarWarsFilmsTable.director)
            .groupBy(StarWarsFilmsTable.director)
        println("Grouped films: $groupedFilms")
    }

    fun limitResults() {
        // Take 2 films after the first one.
        val limitedFilms = StarWarsFilmsTable
            .selectAll()
            .where { StarWarsFilmsTable.sequelId eq ActorsTable.sequelId }
            .limit(2)
            .offset(1)

        println("Limited films: $limitedFilms")
    }

    fun findWithConditionalWhere(directorName: String?, sequelId: Int?) {
        val query = StarWarsFilmsTable.selectAll()

        // Add conditions dynamically
        directorName?.let {
            query.andWhere { StarWarsFilmsTable.director eq it }
        }
        sequelId?.let {
            query.andWhere { StarWarsFilmsTable.sequelId eq it }
        }
    }

    fun findWithConditionalJoin(actorName: String?) {
        transaction {
            val query = StarWarsFilmsTable.selectAll() // Base query

            // Conditionally adjust the query
            actorName?.let { name ->
                query.adjustColumnSet {
                    innerJoin(ActorsTable, { StarWarsFilmsTable.sequelId }, { ActorsTable.sequelId })
                }
                    .adjustSelect {
                        select(StarWarsFilmsTable.columns + ActorsTable.columns)
                    }
                    .andWhere { ActorsTable.name eq name }
            }
        }
    }
}
