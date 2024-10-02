package org.example.examples

import org.example.entities.StarWarsFilmEntity

class DeleteExamples {
    fun deleteFilm() {
        val movie = StarWarsFilmEntity.new {
            name = "The Last Jedi"
            sequelId = 8
            director = "Rian Johnson"
        }
        val deletedMovie = movie.delete()
    }
}
