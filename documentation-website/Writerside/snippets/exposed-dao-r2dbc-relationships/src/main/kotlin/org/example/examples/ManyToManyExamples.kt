package org.example.examples

import kotlinx.coroutines.flow.toList
import org.example.entities.ActorEntity
import org.example.entities.StarWarsFilmEntity
import org.jetbrains.exposed.v1.r2dbc.SizedCollection

const val MOVIE2_SEQUEL_ID = 9

class ManyToManyExamples {
    suspend fun getActors() {
        // create an actor
        val actor = ActorEntity.newSuspend {
            firstname = "Daisy"
            lastname = "Ridley"
        }
        // create film
        val film = StarWarsFilmEntity.newSuspend {
            name = "The Rise of Skywalker"
            sequelId = MOVIE2_SEQUEL_ID
            director = "J.J. Abrams"
            actors = SizedCollection(listOf(actor))
        }

        val filmActors = film.actors.toList()
        filmActors.forEach {
            println(it.firstname)
        }
    }
}
