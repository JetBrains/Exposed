package org.example.examples

import org.example.entities.StarWarsFilmEntity
import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.v1.sql.SqlExpressionBuilder.eq

class UpdateExamples {
    fun updateFilmProperty() {
        val movie = StarWarsFilmEntity.findById(2)
        if (movie != null) {
            /*
                Important: The `movie.name` statement is referenced by line number in `DAO-CRUD-operations.topic`.
                If you add, remove, or modify any lines prior to this one, ensure you update the corresponding
                line numbers in the `code-block` element of the referenced file.
             */
            movie.name = "Episode VIII – The Last Jedi"
            println("The movie has been renamed to ${movie.name}")
        }
    }
    fun updateFilms() {
        // Find by id and update
        val updatedMovie = StarWarsFilmEntity.findByIdAndUpdate(2) {
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
