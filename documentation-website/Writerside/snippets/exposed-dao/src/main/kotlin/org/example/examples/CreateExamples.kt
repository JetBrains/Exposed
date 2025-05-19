package org.example.examples

import org.example.entities.DirectorEntity
import org.example.entities.StarWarsFilmEntity
import org.example.tables.DirectorsTable
import org.example.tables.Genre
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import java.util.*

const val MOVIE_SEQUEL_ID = 8
const val MOVIE2_SEQUEL_ID = 9

class CreateExamples {
    fun createFilms() {
        val movie = StarWarsFilmEntity.new {
            name = "The Last Jedi"
            sequelId = MOVIE_SEQUEL_ID
            director = "Rian Johnson"
        }
        println("Created a new record with name " + movie.name)

        // Create a new record with id
        val movie2 = StarWarsFilmEntity.new(id = 2) {
            name = "The Rise of Skywalker"
            sequelId = MOVIE2_SEQUEL_ID
            director = "J.J. Abrams"
        }
        println("Created a new record with id " + movie2.id)
    }

    // Create a new record with a composite id
    fun createNewWithCompositeId() {
        val directorId = CompositeID {
            it[DirectorsTable.name] = "J.J. Abrams"
            it[DirectorsTable.guildId] = UUID.randomUUID()
        }

        val director = DirectorEntity.new(directorId) {
            genre = Genre.SCI_FI
        }
    }
}
