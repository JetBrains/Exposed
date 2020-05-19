package org.jetbrains.exposed.sql.tests.demo.dao

import org.jetbrains.exposed.dao.id.*
import org.jetbrains.exposed.dao.transaction
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.DEFAULT_ISOLATION_LEVEL
import org.jetbrains.exposed.sql.transactions.DEFAULT_REPETITION_ATTEMPTS
import kotlin.test.Test

object Users : IntIdTable() {
    val name = varchar("name", 50).index()
    val city = reference("city", Cities)
    val age = integer("age")
}

object Cities: IntIdTable() {
    val name = varchar("name", 50)
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var name by Users.name
    var city by City referencedOn Users.city
    var age by Users.age
}

class City(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<City>(Cities)

    var name by Cities.name
    val users by User referrersOn Users.city
}

fun main() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver", user = "root", password = "", manager = { DaoTransactionManager(it, DEFAULT_ISOLATION_LEVEL, DEFAULT_REPETITION_ATTEMPTS) })

    transaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create (Cities, Users)

        val stPete = City.new {
            name = "St. Petersburg"
        }

        val munich = City.new {
            name = "Munich"
        }

        User.new {
            name = "a"
            city = stPete
            age = 5
        }

        User.new {
            name = "b"
            city = stPete
            age = 27
        }

        User.new {
            name = "c"
            city = munich
            age = 42
        }

        println("Cities: ${City.all().joinToString {it.name}}")
        println("Users in ${stPete.name}: ${stPete.users.joinToString {it.name}}")
        println("Adults: ${User.find { Users.age greaterEq 18 }.joinToString {it.name}}")
    }
}

class SamplesDao {
    @Test
    fun ensureSamplesDoesntCrash(){
        main()
    }
}
