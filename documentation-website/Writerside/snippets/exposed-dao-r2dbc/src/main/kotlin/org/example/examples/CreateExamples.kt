package org.example.examples

import org.example.entities.DirectorEntity
import org.example.entities.StarWarsFilmEntity
import org.example.tables.DirectorsTable
import org.example.tables.Genre
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.dao.r2dbc.flushCache
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.uuid.Uuid

const val MOVIE2_SEQUEL_ID = 9
const val SCHEDULED_MOVIE_SEQUEL_ID = 7

class CreateExamples {
    suspend fun createFilms() {
        val previousFilmId = 1

        val movie = StarWarsFilmEntity.newSuspend {
            name = "The Last Jedi"
            sequelId = (StarWarsFilmEntity.findById(previousFilmId)?.sequelId ?: 0) + 1
            director = "Rian Johnson"
        }
        println("Created a new record with name " + movie.name)

        // Create a new record with id
        val movie2 = StarWarsFilmEntity.new(id = 10) {
            name = "The Rise of Skywalker"
            sequelId = MOVIE2_SEQUEL_ID
            director = "J.J. Abrams"
        }
        println("Created a new record with id " + movie2.id)
    }

    // Schedule an insert that is flushed with the next statement or on commit
    fun createScheduledFilm() {
        val scheduledMovie = StarWarsFilmEntity.new {
            name = "The Force Awakens"
            sequelId = SCHEDULED_MOVIE_SEQUEL_ID
            director = "J.J. Abrams"
        }
        println("Scheduled a new record with name " + scheduledMovie.name)
    }

    // Create a new record with a composite id
    suspend fun createNewWithCompositeId() {
        val directorId = CompositeID {
            it[DirectorsTable.name] = "J.J. Abrams"
            it[DirectorsTable.guildId] = Uuid.random()
        }

        val director = DirectorEntity.new(directorId) {
            genre = Genre.SCI_FI
        }
        println("Created a new director with id " + director.id)
    }

    suspend fun batchInsert() {
        suspendTransaction {
            val films = listOf("A New Hope", "The Empire Strikes Back")
                .map { title ->
                    StarWarsFilmEntity.new {
                        name = title
                    }
                }

            flushCache()

            val ids = films.map {
                it.id.value
            }
            println("Created new records with ids $ids")
        }
    }
}
