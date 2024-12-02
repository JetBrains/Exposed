import org.example.tables.StarWarsFilmsTable
import org.example.tables.CitiesTable
import org.example.tables.StarWarsFilmsIntIdTable
import org.jetbrains.exposed.sql.*

/*
    Important: The SQL statements in this file are referenced by line number in `DSL-CRUD-Operations.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

const val MOVIE_SEQUEL_ID = 7
const val MOVIE_SEQUEL_2_ID = 8

class CreateExamples {
    fun createFilmRecords() {
        /*
            INSERT INTO STARWARSFILMS (SEQUEL_ID, "name", DIRECTOR)
            VALUES (7, 'The Force Awakens', 'J.J. Abrams')
        */

        val movie = StarWarsFilmsTable.insert {
            it[sequelId] = MOVIE_SEQUEL_ID
            it[name] = "The Force Awakens"
            it[director] = "J.J. Abrams"
        }
        println(movie)
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
        val insertRow = StarWarsFilmsTable.insert {
            it[sequelId] = MOVIE_SEQUEL_2_ID // column pre-defined with a unique index
            it[name] = "The Last Jedi"
            it[director] = "Rian Johnson"
        }

        val insertIgnoreRows = StarWarsFilmsTable.insertIgnore {
            it[sequelId] = MOVIE_SEQUEL_2_ID
            it[name] = "The Last Jedi"
            it[director] = "Rian Johnson"
        }
        println(insertIgnoreRows)

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
        val allCitiesID = CitiesTable.batchInsert(cityNames) { name ->
            this[CitiesTable.name] = name
        }
    }

    fun batchInsert() {
        data class SWFilmData(val sequelId: Int, val name: String, val director: String)

        val films = listOf(
                SWFilmData(5, "The Empire Strikes Back", "Irvin Kershner"),
                SWFilmData(4, "A New Hope", "George Lucas"),
                SWFilmData(6, "Return of the Jedi", "Richard Marquand")
            )

        StarWarsFilmsTable.batchInsert(films) { (id, name, director) ->
            this[StarWarsFilmsTable.sequelId] = id
            this[StarWarsFilmsTable.name] = name
            this[StarWarsFilmsTable.director] = director
        }
    }
}
