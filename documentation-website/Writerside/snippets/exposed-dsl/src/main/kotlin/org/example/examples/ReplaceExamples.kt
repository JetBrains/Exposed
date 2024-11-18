package org.example.examples

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus

object StarWarsFilmsTable : Table() {
    val sequelId = integer("sequel_id").uniqueIndex()
    val releaseYear= integer("release_year")
    val name = varchar("name", 50)
    val director = varchar("director", 50)
    val rating = double("rating").default(10.0)

    override val primaryKey = PrimaryKey(sequelId, releaseYear)
}

class ReplaceExamples {
    // inserts a new row with default rating
    fun replace() {
        StarWarsFilmsTable.replace {
            it[sequelId] = 9
            it[releaseYear] = 2019
            it[name] = "The Rise of Skywalker"
            it[director] = "JJ Abrams"
        }
        // deletes existing row and inserts new row with set [rating]
        StarWarsFilmsTable.replace {
            it[sequelId] = 9
            it[releaseYear] = 2019
            it[name] = "The Rise of Skywalker"
            it[director] = "JJ Abrams"
            it[rating] = 5.2
        }
    }

    fun replaceWithQuery() {
        val allRowsWithLowRating: Query = StarWarsFilmsTable.selectAll().where {
            StarWarsFilmsTable.rating less 5.0
        }
        StarWarsFilmsTable.replace(allRowsWithLowRating)
    }

    fun replaceWithQueryAndColumns() {
        val oneYearLater = StarWarsFilmsTable.releaseYear.plus(1)
        val allRowsWithNewYear: Query = StarWarsFilmsTable.select(
            oneYearLater, StarWarsFilmsTable.sequelId, StarWarsFilmsTable.director, StarWarsFilmsTable.name
        )
        StarWarsFilmsTable.replace(
            allRowsWithNewYear,
            columns = listOf(
                StarWarsFilmsTable.releaseYear,
                StarWarsFilmsTable.sequelId,
                StarWarsFilmsTable.director,
                StarWarsFilmsTable.name
            )
        )
    }
}
