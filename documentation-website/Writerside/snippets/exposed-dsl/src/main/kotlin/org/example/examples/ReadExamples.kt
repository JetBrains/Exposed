package org.example.examples

import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll

const val MOVIE_SEQUELID = 8
const val MIN_MOVIE_RATING = 5
const val MOVIE_RATING = 4.2

class ReadExamples {

    fun read() {
        /*
            Select specific films columns.

            SELECT STARWARSFILMS."name", STARWARSFILMS.DIRECTOR FROM STARWARSFILMS
            [(The Force Awakens, J.J. Abrams), (The Empire Strikes Back, Irvin Kershner), (A New Hope, George Lucas), (Return of the Jedi, Richard Marquand)]
         */

        val filmAndDirector = StarWarsFilmsTable.select(StarWarsFilmsTable.name, StarWarsFilmsTable.director).map {
            it[StarWarsFilmsTable.name] to it[StarWarsFilmsTable.director]
        }
        println(filmAndDirector)

        /*
            Select only distinct values

            SELECT DISTINCT STARWARSFILMS.DIRECTOR FROM STARWARSFILMS WHERE STARWARSFILMS.SEQUEL_ID < 5
         */

        val directors = StarWarsFilmsTable.select(StarWarsFilmsTable.director)
            .where { StarWarsFilmsTable.sequelId less 5 }.withDistinct()
            .map {
                it[StarWarsFilmsTable.director]
            }

        /*
            Use the DISTINCT ON clause

            SELECT DISTINCT ON (STARWARSFILMS.DIRECTOR) STARWARSFILMS.DIRECTOR, STARWARSFILMS."name"
            FROM STARWARSFILMS
            ORDER BY STARWARSFILMS.DIRECTOR ASC, STARWARSFILMS."name" ASC
         */

        val directorsDistinctOn = StarWarsFilmsTable.select(StarWarsFilmsTable.director, StarWarsFilmsTable.name)
            .withDistinctOn(StarWarsFilmsTable.director)
            .orderBy(
                StarWarsFilmsTable.director to SortOrder.ASC,
                StarWarsFilmsTable.name to SortOrder.ASC
            )
            .map {
                it[StarWarsFilmsTable.name]
            }

        /*
            Sort results in ascending order

             SELECT STARWARSFILMS.DIRECTOR, STARWARSFILMS."name"
             FROM STARWARSFILMS
             ORDER BY STARWARSFILMS.DIRECTOR ASC, STARWARSFILMS."name" ASC
         */

        val filmsByAscOrder = StarWarsFilmsTable.select(StarWarsFilmsTable.director, StarWarsFilmsTable.name)
                .orderBy(
                    StarWarsFilmsTable.director to SortOrder.ASC,
                    StarWarsFilmsTable.name to SortOrder.ASC
                )
                .map {
                    it[StarWarsFilmsTable.name]
                }

        /*
            Sort results in descending order

            SELECT STARWARSFILMS.DIRECTOR, STARWARSFILMS."name"
            FROM STARWARSFILMS
            ORDER BY STARWARSFILMS.DIRECTOR DESC, STARWARSFILMS."name" DESC
         */

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
        /*
            SELECT STARWARSFILMS.SEQUEL_ID, STARWARSFILMS.RELEASE_YEAR, STARWARSFILMS."name", STARWARSFILMS.DIRECTOR, STARWARSFILMS.RATING
            FROM STARWARSFILMS
            WHERE STARWARSFILMS.SEQUEL_ID = 8
         */

        val query = StarWarsFilmsTable.selectAll().where { StarWarsFilmsTable.sequelId eq 6 }

        val queryTraverse = StarWarsFilmsTable.selectAll().where { StarWarsFilmsTable.sequelId eq 6 }.forEach {
            println(it[StarWarsFilmsTable.name])
        }
    }
}
