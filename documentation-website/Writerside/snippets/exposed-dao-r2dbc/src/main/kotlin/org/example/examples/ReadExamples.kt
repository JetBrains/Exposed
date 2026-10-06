package org.example.examples

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.example.entities.*
import org.example.tables.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.uuid.Uuid

const val MOVIE_SEQUELID = 8
const val MIN_MOVIE_RATING = 5
const val MOVIE_RATING = 4.2

class ReadExamples {

    suspend fun readAll() {
        // Read all movies
        val allMovies = StarWarsFilmEntity.all()
        allMovies.toList().forEach({ println(it.name) })

        // Sort results in ascending order
        val moviesByAscOrder = StarWarsFilmEntity.all().toList().sortedBy { it.sequelId }
        moviesByAscOrder.toList().forEach { println(it.sequelId) }

        // Sort results in descending order
        val moviesByDescOrder = StarWarsFilmEntity.all().toList().sortedByDescending { it.sequelId }
        moviesByDescOrder.toList().forEach { println(it.sequelId) }
    }

    suspend fun find() {
        // Get an entity by its id value
        val movie = StarWarsFilmEntity.findById(2)

        if (movie != null) {
            // Read a property value
            val movieName = movie.name
            println("Created a new movie with name $movieName")

            // Read the id value
            val movieId: Int = movie.id.value
            println("The id of the new movie is $movieId")
        }

        // Read all with a condition
        val specificMovie = StarWarsFilmEntity.find { StarWarsFilmsTable.sequelId eq MOVIE_SEQUELID }
        specificMovie.toList().forEach({ println("Found a movie with sequelId " + MOVIE_SEQUELID + " and name " + it.name) })
    }

    // Read an entity with a join to another table
    suspend fun readWithJoin() {
        val query = UsersTable.innerJoin(UserRatingsTable).innerJoin(StarWarsFilmsTable)
            .select(UsersTable.columns)
            .where {
                StarWarsFilmsTable.sequelId eq MOVIE_SEQUELID and (UserRatingsTable.value greater MIN_MOVIE_RATING.toLong())
            }.withDistinct()

        val users = UserEntity.wrapRows(query).toList()
        users.toList().forEach { println(it.name) }
    }

    suspend fun findByCompositeId() {
        val directorId = CompositeID {
            it[DirectorsTable.name] = "J.J. Abrams"
            it[DirectorsTable.guildId] = Uuid.random()
        }

        DirectorEntity.newSuspend(directorId) {
            genre = Genre.SCI_FI
        }

        val director = DirectorEntity.findById(directorId)
        println("Found director $director")
        val directors = DirectorEntity.find { DirectorsTable.id eq directorId }
        directors.toList().forEach({ println(it.genre) })
    }

    suspend fun queriesAsExpressions() {
        // Use a query as an expression to sort cities by the number of users in each city
        CitiesTable.insert {
            it[name] = "Amsterdam"
        }

        val expression = wrapAsExpression<Int>(
            UsersTable.select(UsersTable.id.count())
                .where { CitiesTable.id eq UsersTable.cityId }
        )
        val cities = CitiesTable.selectAll()
            .orderBy(expression, SortOrder.DESC)
            .toList()

        cities.map { println(it[CitiesTable.name]) }
    }

    suspend fun readComputedField() {
        suspendTransaction {
            StarWarsFilmWithRankEntity.new {
                sequelId = MOVIE_SEQUELID
                name = "The Last Jedi"
                rating = MOVIE_RATING
            }
        }

        suspendTransaction {
            StarWarsFilmWithRankEntity
                .find { StarWarsFilmsWithRankTable.name like "The%" }
                .map { it.name to it.rank }
        }
    }
}
