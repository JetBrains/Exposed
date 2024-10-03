package org.example.examples

import org.example.entities.StarWarsFilmEntity

class DeleteExamples {
    fun deleteFilm() {
        val movie = StarWarsFilmEntity.findById(2)
        if (movie != null) {
            val deletedMovie = movie.delete()
            println(deletedMovie)
        }
    }
}
