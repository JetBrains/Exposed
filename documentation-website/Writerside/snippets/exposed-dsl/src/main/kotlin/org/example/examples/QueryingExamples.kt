package org.example.examples

import org.example.tables.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.anyFrom
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/*
    Important: All assigned examples in this file are referenced by name and all unassigned examples by line number in `DSL-Querying-Data.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers and/or names in the `code-block` element of the referenced file.
 */
class QueryingExamples {

    fun printResults() {
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
            .where { StarWarsFilmsTable.sequelId.between(4, 6) }
        println(allBetween.toList())

        val allInList = StarWarsFilmsTable.selectAll()
            .where { StarWarsFilmsTable.sequelId inList listOf(6, 4) }
        println(allInList.toList())

        val topRated = listOf(5 to "Empire Strikes Back", 4 to "A New Hope")
        val multipleInList = StarWarsFilmsTable.selectAll()
            .where {
                StarWarsFilmsTable.sequelId to StarWarsFilmsTable.name inList topRated
            }
        println(multipleInList.toList())

        val anyFromArray = StarWarsFilmsTable.selectAll()
            .where {
                StarWarsFilmsTable.sequelId eq anyFrom(arrayOf(6, 4))
            }
        println(anyFromArray.toList())

        val count = StarWarsFilmsTable.selectAll()
            .where {
                StarWarsFilmsTable.sequelId eq 8
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
