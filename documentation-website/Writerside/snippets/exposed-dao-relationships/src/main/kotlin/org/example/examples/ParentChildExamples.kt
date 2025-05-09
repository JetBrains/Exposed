package org.example.examples

import org.example.entities.DirectorEntity
import org.example.entities.StarWarsFilmWithParentAndChildEntity
import org.example.tables.Genre
import org.jetbrains.exposed.v1.SizedCollection

/*
    Important: This file is referenced by line number in `DAO-Relationships.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

class ParentChildExamples {
    fun querySequels() {
        val director1 = DirectorEntity.new {
            name = "George Lucas"
            genre = Genre.SCI_FI
        }

        val film1 = StarWarsFilmWithParentAndChildEntity.new {
            name = "Star Wars: A New Hope"
            director = director1
        }

        val film2 = StarWarsFilmWithParentAndChildEntity.new {
            name = "Star Wars: The Empire Strikes Back"
            director = director1
        }

        val film3 = StarWarsFilmWithParentAndChildEntity.new {
            name = "Star Wars: Return of the Jedi"
            director = director1
        }

        // Assign parent-child relationships
        film2.prequels = SizedCollection(listOf(film1)) // Empire Strikes Back is a sequel to A New Hope
        film3.prequels = SizedCollection(listOf(film2)) // Return of the Jedi is a sequel to Empire Strikes Back
        film1.sequels = SizedCollection(listOf(film2, film3)) // A New Hope has Empire Strikes Back as a sequel
        film2.sequels = SizedCollection(listOf(film3)) // Empire Strikes Back has Return of the Jedi as a sequel

        film1.sequels.forEach { sequel ->
            println("${sequel.name} is a sequel to ${film1.name}")
        }

        film3.prequels.forEach { prequel ->
            println("${film3.name} has a prequel: ${prequel.name}")
        }
    }
}
