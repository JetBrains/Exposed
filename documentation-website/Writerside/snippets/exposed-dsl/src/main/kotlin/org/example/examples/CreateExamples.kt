package org.example.examples

import org.example.tables.CitiesTable
import org.example.tables.StarWarsFilmsIntIdTable
import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.v1.sql.*

/*
    Important: The contents of this file are referenced by line number in `DSL-CRUD-Operations.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

private const val MOVIE_ORIGINAL_ID = 4
private const val MOVIE_ORIGINAL_2_ID = 5
private const val MOVIE_ORIGINAL_3_ID = 6
private const val MOVIE_SEQUEL_ID = 7
private const val MOVIE_SEQUEL_2_ID = 8

class CreateExamples {
    fun createFilmRecords() {
/*
    INSERT INTO STARWARSFILMS (SEQUEL_ID, "name", DIRECTOR)
    VALUES (7, 'The Force Awakens', 'J.J. Abrams')
*/

        StarWarsFilmsTable.insert {
            it[sequelId] = MOVIE_SEQUEL_ID
            it[name] = "The Force Awakens"
            it[director] = "J.J. Abrams"
        }
    }

    fun createIntIdFilmRecords() {
        /*
             INSERT INTO STAR_WARS_FILMS_TABLE (SEQUEL_ID, "name", DIRECTOR)
             VALUES (7, 'The Force Awakens', 'J.J. Abrams')
         */

        val id = StarWarsFilmsIntIdTable.insertAndGetId {
            it[sequelId] = MOVIE_SEQUEL_ID
            it[name] = "The Force Awakens"
            it[director] = "J.J. Abrams"
        }
        println(id)
    }

    fun insertIgnoreRecords() {
        StarWarsFilmsIntIdTable.insert {
            it[sequelId] = MOVIE_SEQUEL_2_ID // column pre-defined with a unique index
            it[name] = "The Last Jedi"
            it[director] = "Rian Johnson"
        }

        StarWarsFilmsIntIdTable.insertIgnore {
            it[sequelId] = MOVIE_SEQUEL_2_ID
            it[name] = "The Last Jedi"
            it[director] = "Rian Johnson"
        }

/*
    INSERT IGNORE INTO STAR_WARS_FILMS_TABLE (SEQUEL_ID, "name", DIRECTOR)
    VALUES (8, 'The Last Jedi', 'Rian Johnson')
*/

        val rowId = StarWarsFilmsIntIdTable.insertIgnoreAndGetId {
            it[sequelId] = MOVIE_SEQUEL_ID
            it[name] = "The Last Jedi"
            it[director] = "Rian Johnson"
        }
        println(rowId)
    }

    fun simpleBatchInsert() {
        val cityNames = listOf("Paris", "Moscow", "Helsinki")

        CitiesTable.batchInsert(cityNames) { name ->
            this[CitiesTable.name] = name
        }
    }

    fun batchInsert() {
        data class SWFilmData(val sequelId: Int, val name: String, val director: String)

        val films = listOf(
            SWFilmData(MOVIE_ORIGINAL_ID, "A New Hope", "George Lucas"),
            SWFilmData(MOVIE_ORIGINAL_2_ID, "The Empire Strikes Back", "Irvin Kershner"),
            SWFilmData(MOVIE_ORIGINAL_3_ID, "Return of the Jedi", "Richard Marquand")
        )

        StarWarsFilmsTable.batchInsert(films) { (id, name, director) ->
            this[StarWarsFilmsTable.sequelId] = id
            this[StarWarsFilmsTable.name] = name
            this[StarWarsFilmsTable.director] = director
        }
    }
}
