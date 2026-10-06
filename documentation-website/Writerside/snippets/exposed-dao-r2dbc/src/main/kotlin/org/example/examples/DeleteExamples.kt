package org.example.examples

import org.example.entities.StarWarsFilmEntity

class DeleteExamples {
    suspend fun deleteFilm() {
        val movie = StarWarsFilmEntity.findById(2)
        if (movie != null) {
            movie.delete()
        }
    }
}
