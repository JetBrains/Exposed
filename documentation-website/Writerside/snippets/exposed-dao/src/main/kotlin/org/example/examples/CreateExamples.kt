package org.example.examples

import org.example.entities.DirectorEntity
import org.example.entities.StarWarsFilmEntity
import org.example.tables.DirectorsTable
import org.example.tables.Genre
import org.jetbrains.exposed.dao.id.CompositeID
import java.util.*

const val MOVIE_SEQUELID = 8
const val MOVIE2_SEQUELID = 9

class CreateExamples {
    fun createFilms() {
        val movie = StarWarsFilmEntity.new {
            name = "The Last Jedi"
            sequelId = MOVIE_SEQUELID
            director = "Rian Johnson"
        }
        println(movie)

        // Create a new record with id
        val movie2 = StarWarsFilmEntity.new(id = 2) {
            name = "The Rise of Skywalker"
            sequelId = MOVIE2_SEQUELID
            director = "J.J. Abrams"
        }
        println(movie2)
    }

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
