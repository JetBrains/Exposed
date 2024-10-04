package org.example.examples

import org.example.entities.StarWarsFilmEntity

class DeleteExamples {
/*
    Delete a record.

    Important: The `movie.delete` statement is referenced by line number in `DAO-CRUD-operations.topic`.
    If you add, remove, or modify any lines prior to this one, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/
    fun deleteFilm() {
        val movie = StarWarsFilmEntity.findById(2)
        if (movie != null) {
            movie.delete()
        }
    }
}
