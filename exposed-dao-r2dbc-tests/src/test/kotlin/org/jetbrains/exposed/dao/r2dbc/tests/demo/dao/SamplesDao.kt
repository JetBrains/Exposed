package org.jetbrains.exposed.dao.r2dbc.tests.demo.dao

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.relationships.referencedOnSuspend
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import kotlin.test.Test

object Users : IntIdTable() {
    val name = varchar("name", 50).index()
    val city = reference("city", Cities)
    val age = integer("age")
}

object Cities : IntIdTable() {
    val name = varchar("name", 50)
}

class User(id: EntityID<Int>) : IntR2dbcEntity(id) {
    companion object : IntR2dbcEntityClass<User>(Users)

    var name by Users.name
    val city by City referencedOnSuspend Users.city
    var age by Users.age
}

class City(id: EntityID<Int>) : IntR2dbcEntity(id) {
    companion object : IntR2dbcEntityClass<City>(Cities)

    var name by Cities.name
    val users by User referrersOnSuspend Users.city
}

fun main() = runBlocking {
    Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
    R2dbcDatabase.connect("r2dbc:h2:mem:///test", user = "root", password = "")

    suspendTransaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create(Cities, Users)

        val stPete = City.new {
            name = "St. Petersburg"
        }

        val munich = City.new {
            name = "Munich"
        }

        User.new {
            name = "a"
            city set stPete
            age = 5
        }

        User.new {
            name = "b"
            city set stPete
            age = 27
        }

        User.new {
            name = "c"
            city set munich
            age = 42
        }

        println("Cities: ${City.all().toList().joinToString { it.name }}")
        println("Users in ${stPete.name}: ${stPete.users().toList().joinToString { it.name }}")
        println("Adults: ${User.find { Users.age greaterEq 18 }.toList().joinToString { it.name }}")
    }
}

class SamplesDao {
    @Test
    fun ensureSamplesDoesntCrash() {
        main()
    }
}
