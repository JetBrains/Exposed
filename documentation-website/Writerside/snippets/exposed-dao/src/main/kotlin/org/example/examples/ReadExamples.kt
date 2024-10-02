package org.example.examples

import org.example.entities.DirectorEntity
import org.example.entities.StarWarsFilmEntity
import org.example.entities.StarWarsWFilmWithRankEntity
import org.example.entities.UserEntity
import org.example.tables.CitiesTable
import org.example.tables.DirectorsTable
import org.example.tables.StarWarsFilmsTable
import org.example.tables.UserRatingsTable
import org.example.tables.UsersTable
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.wrapAsExpression
import java.util.*

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

        if(movie != null) {
            // Read a property value
            val movieName = movie.name
            println("Created a new film named $movieName")

            // Read the id value
            val movieId: Int = movie.id.value
            println("The id of the new movie is $movieId")
        }

        // Read all with a condition
        val specificMovie = StarWarsFilmEntity.find { StarWarsFilmsTable.sequelId eq 8 }
        specificMovie.forEach({ println("Found a movie with sequelId 8 and name " + it.name) })
    }

    // Read an entity with a join to another table
    fun readWithJoin() {
        val query = UsersTable.innerJoin(UserRatingsTable).innerJoin(StarWarsFilmsTable)
            .select(UsersTable.columns)
            .where {
                StarWarsFilmsTable.sequelId eq 2 and (UserRatingsTable.value greater 5)
            }.withDistinct()

        val users = UserEntity.wrapRows(query).toList()
        users.map { println(it.name) }
    }

    fun findByCompositeId() {
        // Find records by composite id
        val directorId = CompositeID {
            it[DirectorsTable.name] = "J.J. Abrams"
            it[DirectorsTable.guildId] = UUID.randomUUID()
        }

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
        StarWarsWFilmWithRankEntity.new {
            sequelId = 8
            name = "The Last Jedi"
            rating = 4.2
        }

        StarWarsWFilmWithRankEntity.find { StarWarsFilmsTable.name like "The%" }.map { it.name to it.rank }
    }

}
