package org.example.examples

import org.example.entities.StarWarsFilmEntity
import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class UpdateExamples {
    fun updateFilmProperty() {
        val movie = StarWarsFilmEntity.findById(2)
        if (movie != null) {
            movie.name = "Episode VIII – The Last Jedi"
        }
    }
    fun updateFilms() {
        // Find by id and update
        val updatedMovie = StarWarsFilmEntity.findByIdAndUpdate(MOVIE_ID) {
            it.name = "Episode VIII – The Last Jedi"
        }
        println(updatedMovie?.name)

        // Find a single record by a condition and update
        val updatedMovie2 = StarWarsFilmEntity.findSingleByAndUpdate(StarWarsFilmsTable.name eq "The Last Jedi") {
            it.name = "Episode VIII – The Last Jedi"
        }
        println(updatedMovie2?.name)
    }
}
