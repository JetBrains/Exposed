package org.example.examples

import org.example.entities.StarWarsFilmEntity
import org.example.entities.UserEntity
import org.example.entities.UserRatingEntity

const val MOVIE_SEQUEL_ID = 8
const val MOVIE_RATING = 4L

class OneToManyExamples {
    fun queryRatings() {
        // create film
        val starWarsFilm = StarWarsFilmEntity.new {
            name = "The Last Jedi"
            sequelId = MOVIE_SEQUEL_ID
            director = "Rian Johnson"
        }
        val user1 = UserEntity.new {
            name = "johnsmith"
        }

        val filmRating = UserRatingEntity.new {
            value = MOVIE_RATING
            film = starWarsFilm
            user = user1
        }

        // returns a StarWarsFilmEntity object
        val film = filmRating.film
        println(film)

        // returns all UserRatingWithOptionalUserEntity objects with this movie as film
        val filmRatings = starWarsFilm.ratings
        println(filmRatings)

        // returns a UserRatingEntity object
//        val userRating = user1.rating
//        println(userRating)
    }
}
