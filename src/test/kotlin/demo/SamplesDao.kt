package demo.dao

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*

object Users : IdTable() {
    val name = varchar("name", 50).index()
    val city = reference("city", Cities)
    val age = integer("age")
}

object Cities: IdTable() {
    val name = varchar("name", 50)
}

class User(id: EntityID) : Entity(id) {
    companion object : EntityClass<User>(Users)

    var name by Users.name
    var city by City referencedOn Users.city
    var age by Users.age
}

class City(id: EntityID) : Entity(id) {
    companion object : EntityClass<City>(Cities)

    var name by Cities.name
    val users by User referrersOn Users.city
}

fun main(args: Array<String>) {
    val db = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")

    db.transaction {
        logger.addLogger(StdOutSqlLogger())

        create (Cities, Users)

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
