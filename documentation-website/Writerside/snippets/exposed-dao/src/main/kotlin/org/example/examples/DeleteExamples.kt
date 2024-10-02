package org.example.examples

import org.example.entities.StarWarsFilmEntity

const val SEQUEL_ID = 8

class DeleteExamples {
    fun deleteFilm() {
        val movie = StarWarsFilmEntity.new {
            name = "The Last Jedi"
            sequelId = SEQUEL_ID
            director = "Rian Johnson"
        }
        val deletedMovie = movie.delete()
        println(deletedMovie)
    }
}
