package org.example.examples

import org.example.entities.DirectorEntity
import org.example.entities.StarWarsFilmEntity
import org.example.tables.DirectorsTable
import org.example.tables.Genre
import org.jetbrains.exposed.dao.id.CompositeID
import java.util.*

class CreateExamples {
    fun createFilms() {
        val movie = StarWarsFilmEntity.new {
            name = "The Last Jedi"
            sequelId = 8
            director = "Rian Johnson"
        }

        // Create a new record with id
        val movie2 = StarWarsFilmEntity.new(id = 2) {
            name = "The Rise of Skywalker"
            sequelId = 9
            director = "J.J. Abrams"
        }
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
