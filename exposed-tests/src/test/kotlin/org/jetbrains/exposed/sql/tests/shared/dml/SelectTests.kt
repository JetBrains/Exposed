package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class SelectTests : DatabaseTestsBase() {
    // select expressions
    @Test
    fun testSelect() {
        withCitiesAndUsers { _, users, _ ->
            users.select { users.id.eq("andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectAnd() {
        withCitiesAndUsers { cities, users, userData ->
            users.select { users.id.eq("andrey") and users.name.eq("Andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectOr() {
        withCitiesAndUsers { cities, users, userData ->
            users.select { users.id.eq("andrey") or users.name.eq("Andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectNot() {
        withCitiesAndUsers { cities, users, userData ->
            users.select { org.jetbrains.exposed.sql.not(users.id.eq("andrey")) }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                if (userId == "andrey") {
                    error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSizedIterable() {
        withCitiesAndUsers { cities, users, userData ->
            assertEquals(false, cities.selectAll().empty())
            assertEquals(true, cities.select { cities.name eq "Qwertt" }.empty())
            assertEquals(0, cities.select { cities.name eq "Qwertt" }.count())
            assertEquals(3, cities.selectAll().count())
        }
    }



    @Test
    fun testInList01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select { users.id inList listOf("andrey", "alex") }.orderBy(users.name).toList()

            assertEquals(2, r.size)
            assertEquals("Alex", r[0][users.name])
            assertEquals("Andrey", r[1][users.name])
        }
    }

    @Test
    fun testInList02() {
        withCitiesAndUsers { cities, users, userData ->
            val cityIds = cities.selectAll().map { it[cities.id] }.take(2)
            val r = cities.select { cities.id inList cityIds }

            assertEquals(2, r.count())
        }
    }

    @Test
    fun testInSubQuery01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = cities.select { cities.id inSubQuery cities.slice(cities.id).select { cities.id eq 2 } }
            assertEquals(1, r.count())
        }
    }


    @Test
    fun testSelectDistinct() {
        val tbl = DMLTestsData.Cities
        withTables(tbl) {
            tbl.insert { it[tbl.name] = "test" }
            tbl.insert { it[tbl.name] = "test" }

            assertEquals(2, tbl.selectAll().count())
            assertEquals(2, tbl.selectAll().withDistinct().count())
            assertEquals(1, tbl.slice(tbl.name).selectAll().withDistinct().count())
            assertEquals("test", tbl.slice(tbl.name).selectAll().withDistinct().single()[tbl.name])
        }
    }

    @Test
    fun testCompoundOp() {
        withCitiesAndUsers { cities, users, _ ->
            val allUsers = setOf(
                "Andrey",
                "Sergey",
                "Eugene",
                "Alex",
                "Something"
            )
            val orOp = allUsers.map { Op.build { users.name eq it } }.compoundOr()
            val userNamesOr = users.select(orOp).map { it[users.name] }.toSet()
            assertEquals(allUsers, userNamesOr)

            val andOp = allUsers.map { Op.build { users.name eq it } }.compoundAnd()
            assertEquals(0, users.select(andOp).count())
        }
    }
}