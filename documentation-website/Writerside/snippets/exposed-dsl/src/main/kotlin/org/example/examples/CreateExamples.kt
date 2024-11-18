import org.example.tables.MAX_VARCHAR_LENGTH
import org.example.tables.StarWarsFilmsTable
import org.example.tables.CitiesTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*

const val MOVIE_SEQUEL_ID = 8

//IntIdTable to use in examples of methods that are only compatible with tables of IdTable type
object StarWarsFilmsIntIdTable : IntIdTable("star_wars_films") {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", MAX_VARCHAR_LENGTH)
    val director = varchar("director", MAX_VARCHAR_LENGTH)
}

class CreateExamples {
    fun createFilms() {
        /*
        INSERT INTO STARWARSFILMS (SEQUEL_ID, "name", DIRECTOR)
        VALUES (8, 'The Last Jedi', 'Rian Johnson')
         */

        val movie = StarWarsFilmsTable.insert {
            it[sequelId] = MOVIE_SEQUEL_ID
            it[name] = "St. Petersburg"
            it[director] = "Rian Johnson"
            it[name] = "The Last Jedi"
        }

        val id = StarWarsFilmsIntIdTable.insertAndGetId {
            it[sequelId] = MOVIE_SEQUEL_ID
            it[name] = "St. Petersburg"
            it[director] = "Rian Johnson"
            it[name] = "The Last Jedi"
        }
    }

    fun createIgnore() {
        val insertedRows = StarWarsFilmsTable.insert {
            it[sequelId] = 8 // column pre-defined with a unique index
            it[name] = "The Last Jedi"
            it[director] = "Rian Johnson"
        }

        val insertIgnoredRows = StarWarsFilmsTable.insertIgnore {
            it[sequelId] = 8
            it[name] = "The Rise of Skywalker"
            it[director] = "JJ Abrams"
        }

        /*
            INSERT IGNORE INTO STARWARSFILMS (SEQUEL_ID, "name", DIRECTOR)
            VALUES (8, 'The Last Jedi', 'Rian Johnson')
        */

        val rowId = StarWarsFilmsIntIdTable.insertIgnoreAndGetId {
            it[sequelId] = MOVIE_SEQUEL_ID
            it[name] = "The Last Jedi"
            it[director] = "Rian Johnson"
        }
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
                SWFilmData(7, "The Force Awakens", "JJ Abrams")
            )

        StarWarsFilmsTable.batchInsert(films) { (id, name, director) ->
            this[StarWarsFilmsTable.sequelId] = id
            this[StarWarsFilmsTable.name] = name
            this[StarWarsFilmsTable.director] = director
        }

        StarWarsFilmsTable.selectAll().count() // 3
    }
}
