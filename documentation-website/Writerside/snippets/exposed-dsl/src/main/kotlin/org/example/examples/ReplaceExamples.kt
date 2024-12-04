package org.example.examples

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus

private const val MOVIE_SEQUEL_3_ID = 9
private const val MOVIE_3_RELEASE_YEAR = 2019
private const val MOVIE_RATING = 5.2
private const val MAX_VARCHAR_LENGTH = 2019
private const val LOW_RAITING_THRESHOLD = 5.0
private const val DEFAULT_RATING = 10.0

object StarWarsFilmsTable : Table() {
    val sequelId = integer("sequel_id").uniqueIndex()
    val releaseYear = integer("release_year")
    val name = varchar("name", MAX_VARCHAR_LENGTH)
    val director = varchar("director", MAX_VARCHAR_LENGTH)
    val rating = double("rating").default(DEFAULT_RATING)

    override val primaryKey = PrimaryKey(sequelId, releaseYear)
}

class ReplaceExamples {
    // inserts a new row with default rating
    fun replace() {
        StarWarsFilmsTable.replace {
            it[sequelId] = MOVIE_SEQUEL_3_ID
            it[releaseYear] = MOVIE_3_RELEASE_YEAR
            it[name] = "The Rise of Skywalker"
            it[director] = "JJ Abrams"
        }
        // deletes existing row and inserts new row with set [rating]
        StarWarsFilmsTable.replace {
            it[sequelId] = MOVIE_SEQUEL_3_ID
            it[releaseYear] = MOVIE_3_RELEASE_YEAR
            it[name] = "The Rise of Skywalker"
            it[director] = "JJ Abrams"
            it[rating] = MOVIE_RATING
        }
    }

    fun replaceWithQuery() {
        val allRowsWithLowRating: Query = StarWarsFilmsTable.selectAll().where {
            StarWarsFilmsTable.rating less LOW_RAITING_THRESHOLD
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
