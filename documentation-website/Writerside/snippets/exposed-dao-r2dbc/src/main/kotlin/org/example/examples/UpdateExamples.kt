package org.example.examples

import org.example.entities.StarWarsFilmEntity
import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

const val NEW_MOVIE_SEQUEL_ID = 6
const val UPDATED_MOVIE_ID = 10

class UpdateExamples {
    suspend fun updateFilmProperty() {
        val movie = StarWarsFilmEntity.findById(UPDATED_MOVIE_ID)
        if (movie != null) {
            movie.name = "Episode VIII – The Last Jedi"
            println("The movie has been renamed to ${movie.name}")
        }
    }

    suspend fun updateFilms() {
        // Find by id and update
        val updatedMovie = StarWarsFilmEntity.findByIdAndUpdate(UPDATED_MOVIE_ID) {
            it.name = "Episode VIII – The Last Jedi"
        }
        println(updatedMovie?.name)

        // Find a single record by a condition and update
        val updatedMovie2 = StarWarsFilmEntity.findSingleByAndUpdate(StarWarsFilmsTable.name eq "The Last Jedi") {
            it.name = "Episode VIII – The Last Jedi"
        }
        println(updatedMovie2?.name)
    }

    suspend fun updateInNewTransaction() {
        val movie = suspendTransaction {
            StarWarsFilmEntity.newSuspend {
                name = "Episode VIII – The Last Jedi"
                sequelId = NEW_MOVIE_SEQUEL_ID
                director = "Unknown"
            }
        }
        suspendTransaction {
            StarWarsFilmEntity.attach(movie)
            movie.director = "Rian Johnson"
        }
    }
}
