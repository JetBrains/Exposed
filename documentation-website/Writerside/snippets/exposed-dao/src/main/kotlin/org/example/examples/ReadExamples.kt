package org.example.examples

import org.example.entities.*
import org.example.tables.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

const val MOVIE_SEQUELID = 8
const val MIN_MOVIE_RATING = 5
const val MOVIE_RATING = 4.2

class ReadExamples {

    fun readAll() {
        // Read all movies
        val allMovies = StarWarsFilmEntity.all()
        allMovies.forEach({ println(it.name) })

        // Sort results in ascending order
        val moviesByAscOrder = StarWarsFilmEntity.all().sortedBy { it.sequelId }
        moviesByAscOrder.map { println(it.sequelId) }

        // Sort results in descending order
        val moviesByDescOrder = StarWarsFilmEntity.all().sortedByDescending { it.sequelId }
        moviesByDescOrder.map { println(it.sequelId) }
    }

    fun find() {
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
        specificMovie.forEach({ println("Found a movie with sequelId " + MOVIE_SEQUELID + " and name " + it.name) })
    }

    // Read an entity with a join to another table
    fun readWithJoin() {
        val query = UsersTable.innerJoin(UserRatingsTable).innerJoin(StarWarsFilmsTable)
            .select(UsersTable.columns)
            .where {
                StarWarsFilmsTable.sequelId eq MOVIE_SEQUELID and (UserRatingsTable.value greater MIN_MOVIE_RATING.toLong())
            }.withDistinct()

        val users = UserEntity.wrapRows(query).toList()
        users.map { println(it.name) }
    }

    /*
        Find records by composite id.

        Important: The SQL query is referenced by line number in `DAO-CRUD-operations.topic`.
        If you add, remove, or modify any lines before the SELECT statement, ensure you update the corresponding
        line numbers in the `code-block` element of the referenced file.

        SELECT DIRECTORS."name", DIRECTORS.GUILD_ID, DIRECTORS.GENRE
        FROM DIRECTORS
        WHERE (DIRECTORS."name" = 'J.J. Abrams')
        AND (DIRECTORS.GUILD_ID = '2cc64f4f-1a2c-41ce-bda1-ee492f787f4b')
     */
    fun findByCompositeId() {
        val directorId = CompositeID {
            it[DirectorsTable.name] = "J.J. Abrams"
            it[DirectorsTable.guildId] = Uuid.random()
        }

        DirectorEntity.new(directorId) {
            genre = Genre.SCI_FI
        }

        val director = DirectorEntity.findById(directorId)
        println("Found director $director")
        val directors = DirectorEntity.find { DirectorsTable.id eq directorId }
        directors.forEach({ println(it.genre) })
    }

    fun queriesAsExpressions() {
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

    fun readComputedField() {
        transaction {
            StarWarsFilmWithRankEntity.new {
                sequelId = MOVIE_SEQUELID
                name = "The Last Jedi"
                rating = MOVIE_RATING
            }
        }

        transaction {
            StarWarsFilmWithRankEntity
                .find { StarWarsFilmsWithRankTable.name like "The%" }
                .map { it.name to it.rank }
        }
    }
}
