package org.jetbrains.exposed.sql.tests.demo.sql

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume
import org.junit.Test
import kotlin.test.assertEquals

object Users : Table() {
    val id: Column<String> = varchar("id", 10)
    val name: Column<String> = varchar("name", length = 50)
    val cityId: Column<Int?> = (integer("city_id") references Cities.id).nullable()

    override val primaryKey = PrimaryKey(id, name = "PK_User_ID") // name is optional here
}

object Cities : Table() {
    val id: Column<Int> = integer("id").autoIncrement()
    val name: Column<String> = varchar("name", 50)

    override val primaryKey = PrimaryKey(id, name = "PK_Cities_ID")
}

fun main() {
    Assume.assumeTrue(TestDB.H2 in TestDB.enabledInTests())
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver", user = "root", password = "")

    transaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create(Cities, Users)

        val saintPetersburgId = Cities.insert {
            it[name] = "St. Petersburg"
        } get Cities.id

        val munichId = Cities.insert {
            it[name] = "Munich"
        } get Cities.id

        val pragueId = Cities.insert {
            it.update(name, stringLiteral("   Prague   ").trim().substring(1, 2))
        }[Cities.id]

        val pragueName = Cities.select { Cities.id eq pragueId }.single()[Cities.name]
        assertEquals(pragueName, "Pr")

        Users.insert {
            it[id] = "andrey"
            it[name] = "Andrey"
            it[Users.cityId] = saintPetersburgId
        }

        Users.insert {
            it[id] = "sergey"
            it[name] = "Sergey"
            it[Users.cityId] = munichId
        }

        Users.insert {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[Users.cityId] = munichId
        }

        Users.insert {
            it[id] = "alex"
            it[name] = "Alex"
            it[Users.cityId] = null
        }

        Users.insert {
            it[id] = "smth"
            it[name] = "Something"
            it[Users.cityId] = null
        }

        Users.update({ Users.id eq "alex" }) {
            it[name] = "Alexey"
        }

        Users.deleteWhere { Users.name like "%thing" }

        println("All cities:")

        for (city in Cities.selectAll()) {
            println("${city[Cities.id]}: ${city[Cities.name]}")
        }

        println("Manual join:")
        (Users innerJoin Cities)
            .slice(Users.name, Cities.name)
            .select {
                (Users.id.eq("andrey") or Users.name.eq("Sergey")) and
                    Users.id.eq("sergey") and Users.cityId.eq(Cities.id)
            }.forEach {
                println("${it[Users.name]} lives in ${it[Cities.name]}")
            }

        println("Join with foreign key:")

        (Users innerJoin Cities)
            .slice(Users.name, Users.cityId, Cities.name)
            .select { Cities.name.eq("St. Petersburg") or Users.cityId.isNull() }
            .forEach {
                if (it[Users.cityId] != null) {
                    println("${it[Users.name]} lives in ${it[Cities.name]}")
                } else {
                    println("${it[Users.name]} lives nowhere")
                }
            }

        println("Functions and group by:")

        ((Cities innerJoin Users)
            .slice(Cities.name, Users.id.count())
            .selectAll()
            .groupBy(Cities.name)
            ).forEach {
                val cityName = it[Cities.name]
                val userCount = it[Users.id.count()]

                if (userCount > 0) {
                    println("$userCount user(s) live(s) in $cityName")
                } else {
                    println("Nobody lives in $cityName")
                }
            }

        SchemaUtils.drop(Users, Cities)
    }
}

class SamplesSQL {
    @Test
    fun ensureSamplesDoesntCrash() {
        main()
    }
}
