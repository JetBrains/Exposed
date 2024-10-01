package org.example

import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun main() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")

    transaction {
        addLogger(StdOutSqlLogger)
        createFilms()
        createDirectors()
        createUsersAndRatings()
    }
}

fun createFilms() {
    SchemaUtils.create(StarWarsFilmsTable)

    //Create a new record
    val movie = StarWarsFilmEntity.new {
        name = "The Last Jedi"
        sequelId = 8
        director = "Rian Johnson"
    }

    //Create a new record with id
    StarWarsFilmEntity.new(id = 2) {
        name = "The Rise of Skywalker"
        sequelId = 9
        director = "J.J. Abrams"
    }

    //Read a property value
    val movieName = movie.name
    println("Created a new film named $movieName")

    //Read the id value
    val movieId: Int = movie.id.value
    println("The id of the new movie is $movieId")

    //Read all movies
    val allMovies = StarWarsFilmEntity.all()
    allMovies.forEach({ println(it.name)})

    //Sort results in ascending order
    val moviesByAscOrder = StarWarsFilmEntity.all().sortedBy { it.sequelId }
    moviesByAscOrder.map { println(it.sequelId) }

    //Sort results in descending order
    val moviesByDescOrder = StarWarsFilmEntity.all().sortedByDescending { it.sequelId }
    moviesByDescOrder.map { println(it.sequelId) }

    //Read all with a condition
    val specificMovie = StarWarsFilmEntity.find { StarWarsFilmsTable.sequelId eq 8 }
     specificMovie.forEach({ println("Found a movie with sequelId 8 and name "+it.name)})

    //Get an entity by its id value
    val fifthMovie = StarWarsFilmEntity.findById(2)
    println(fifthMovie?.name)

    //Update an entity value
    movie.name = "Episode VIII – The Last Jedi"

    //Find by id and update
    val updatedMovie = StarWarsFilmEntity.findByIdAndUpdate(5) {
        it.name = "Episode VIII – The Last Jedi"
    }
    println(updatedMovie?.name)

    //Find a single record by a condition and update
    val updatedMovie2 = StarWarsFilmEntity.findSingleByAndUpdate(StarWarsFilmsTable.name eq "The Last Jedi") {
        it.name = "Episode VIII – The Last Jedi"
    }
    println(updatedMovie2?.name)

    //Delete a record
    movie.delete()

}

fun createDirectors() {
    SchemaUtils.create(DirectorsTable)

    val directorId = CompositeID {
        it[DirectorsTable.name] = "J.J. Abrams"
        it[DirectorsTable.guildId] = UUID.randomUUID()
    }

    DirectorEntity.new(directorId) {
        genre = Genre.SCI_FI
    }

    //find a single record by composite id
    val director = DirectorEntity.findById(directorId)
    if (director != null) {
        println(director.genre)
    }

    //Find directors by composite id
    val directors = DirectorEntity.find { DirectorsTable.id eq directorId }
    directors.forEach({ println(it.genre)})
}

fun createUsersAndRatings() {
    SchemaUtils.create(UsersTable)
    SchemaUtils.create(UserRatingsTable)
    SchemaUtils.create(CitiesTable)

    //Read an entity with a join to another table
    val query = UsersTable.innerJoin(UserRatingsTable).innerJoin(StarWarsFilmsTable)
        .select(UsersTable.columns)
        .where {
            StarWarsFilmsTable.sequelId eq 2 and (UserRatingsTable.value greater 5)
        }.withDistinct()

    val users = UserEntity.wrapRows(query).toList()
    users.map { println(it.name) }

    CitiesTable.insert {
        it[name] = "Amsterdam"
    }
    //Use a query as an expression to sort cities by the number of users in each city
    val expression = wrapAsExpression<Int>(UsersTable.select(UsersTable.id.count()).where { CitiesTable.id eq UsersTable.cityId })
    val cities = CitiesTable.selectAll().orderBy(expression, SortOrder.DESC).toList()

    cities.map { println(it[CitiesTable.name]) }
}
