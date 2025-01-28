package org.example.examples

import org.example.entities.StarWarsFilmEntity
import org.example.entities.ActorEntity
import org.jetbrains.exposed.sql.SizedCollection

const val MOVIE2_SEQUEL_ID = 9

class ManyToManyExamples {
    fun getActors() {
        // create an actor
        val actor = ActorEntity.new{
            firstname = "Daisy"
            lastname = "Ridley"
        }
        // create film
        val film = StarWarsFilmEntity.new{
            name = "The Rise of Skywalker"
            sequelId = MOVIE2_SEQUEL_ID
            director = "J.J. Abrams"
            actors = SizedCollection(listOf(actor))
        }

        val filmActors = film.actors
        filmActors.forEach {
            println(it.firstname)
        }
    }
}
