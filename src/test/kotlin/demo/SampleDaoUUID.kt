package demo.dao.uuid

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object Users : UUIDTable("users") {
  val name = varchar("name", 50).index()
  val city = reference("city_id", Cities)
  val age = integer("age")
}

object Cities: UUIDTable("cities") {
  val name = varchar("name", 50)
}

class User(id: EntityID<UUID>) : UUIDEntity(id) {
  companion object : UUIDEntityClass<User>(Users)

  var name by Users.name
  var city by City referencedOn Users.city
  var age by Users.age
}

class City(id: EntityID<UUID>) : UUIDEntity(id) {
  companion object : UUIDEntityClass<City>(Cities)

  var name by Cities.name
  val users by User referrersOn Users.city
}

fun main(args: Array<String>) {
  Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")

  transaction {
    logger.addLogger(StdOutSqlLogger)

    // Create the tables
    SchemaUtils.create (Cities, Users)

    // Create Cities
    val stPete = City.new {
      name = "St. Petersburg"
    }

    val munich = City.new {
      name = "Munich"
    }

    // Create Users
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
