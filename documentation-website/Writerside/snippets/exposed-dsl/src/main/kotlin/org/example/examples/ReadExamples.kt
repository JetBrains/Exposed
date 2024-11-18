package org.example.examples

import org.example.tables.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll

const val MOVIE_SEQUELID = 8
const val MIN_MOVIE_RATING = 5
const val MOVIE_RATING = 4.2

class ReadExamples {

    fun read() {
        // Select specific films columns
        val filmAndDirector = StarWarsFilmsTable.select(StarWarsFilmsTable.name, StarWarsFilmsTable.director).map {
            it[StarWarsFilmsTable.name] to it[StarWarsFilmsTable.director]
        }

        // Select only distinct values
        val directors = StarWarsFilmsTable.select(StarWarsFilmsTable.director)
            .where { StarWarsFilmsTable.sequelId less 5 }.withDistinct()
            .map {
                it[StarWarsFilmsTable.director]
            }

        // Use the DISTINCT ON clause
        val directorsDistinctOn = StarWarsFilmsTable.select(StarWarsFilmsTable.director, StarWarsFilmsTable.name)
            .withDistinctOn(StarWarsFilmsTable.director)
            .orderBy(
                StarWarsFilmsTable.director to SortOrder.ASC,
                StarWarsFilmsTable.name to SortOrder.ASC
            )
            .map {
                it[StarWarsFilmsTable.name]
            }

        // Sort results in ascending order
        val filmsByAscOrder = StarWarsFilmsTable.select(StarWarsFilmsTable.director, StarWarsFilmsTable.name)
                .orderBy(
                    StarWarsFilmsTable.director to SortOrder.ASC,
                    StarWarsFilmsTable.name to SortOrder.ASC
                )
                .map {
                    it[StarWarsFilmsTable.name]
                }

        // Sort results in descending order
        val filmsByDescOrder = StarWarsFilmsTable.select(StarWarsFilmsTable.director, StarWarsFilmsTable.name)
            .orderBy(
                StarWarsFilmsTable.director to SortOrder.DESC,
                StarWarsFilmsTable.name to SortOrder.DESC
            )
            .map {
                it[StarWarsFilmsTable.name]
            }
    }

    fun readAll() {
        val query = StarWarsFilmsTable.selectAll().where { StarWarsFilmsTable.sequelId eq 8 }

        val queryTraverse = StarWarsFilmsTable.selectAll().where { StarWarsFilmsTable.sequelId eq 8 }.forEach {
            println(it[StarWarsFilmsTable.name])
        }
    }
}
