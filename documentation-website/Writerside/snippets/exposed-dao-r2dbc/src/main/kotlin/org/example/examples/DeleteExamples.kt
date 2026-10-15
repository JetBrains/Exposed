package org.example.examples

import org.example.entities.StarWarsFilmEntity

@Suppress("MagicNumber")
class DeleteExamples {
    suspend fun deleteFilm() {
        val movie = StarWarsFilmEntity.findById(10)
        if (movie != null) {
            movie.delete()
        }
    }
}
