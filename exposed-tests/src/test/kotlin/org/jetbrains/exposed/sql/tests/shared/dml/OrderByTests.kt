package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.h2Mode
import org.junit.Test

class OrderByTests : DatabaseTestsBase() {
    @Test
    fun orderBy01() {
        withCitiesAndUsers {
            users.selectAll()
                .orderBy(users.id)
                .toList().let { r ->
                    assertEquals("alex", r[0][users.id])
                    assertEquals("andrey", r[1][users.id])
                    assertEquals("eugene", r[2][users.id])
                    assertEquals("sergey", r[3][users.id])
                    assertEquals("smth", r[4][users.id])
                    assertEquals(5, r.size)
                }

            scopedUsers.selectAll()
                .orderBy(scopedUsers.id)
                .toList().let { r ->
                    assertEquals("eugene", r[0][scopedUsers.id])
                    assertEquals("sergey", r[1][scopedUsers.id])
                    assertEquals(2, r.size)
                }

            scopedUsers.stripDefaultFilter()
                .selectAll()
                .orderBy(scopedUsers.id)
                .map { it[scopedUsers.id] }
                .let { assertEqualLists(listOf("alex", "andrey", "eugene", "sergey", "smth"), it) }
        }
    }

    private fun isNullFirst() = when (currentDialectTest) {
        is OracleDialect, is PostgreSQLDialect -> true
        is H2Dialect -> currentDialectTest.h2Mode in listOf(H2CompatibilityMode.PostgreSQL, H2CompatibilityMode.Oracle)
        else -> false
    }

    @Test
    fun orderBy02() {
        withCitiesAndUsers {
            users.selectAll()
                .orderBy(users.cityId, SortOrder.DESC)
                .orderBy(users.id)
                .toList().let { r ->
                    assertEquals(5, r.size)
                    val usersWithoutCities = listOf("alex", "smth")
                    val otherUsers = listOf("eugene", "sergey", "andrey")
                    val expected = if (isNullFirst()) usersWithoutCities + otherUsers
                    else otherUsers + usersWithoutCities
                    expected.forEachIndexed { index, e ->
                        assertEquals(e, r[index][users.id])
                    }
                }

            scopedUsers.slice(scopedUsers.id)
                .selectAll()
                .orderBy(scopedUsers.name, SortOrder.ASC)
                .orderBy(scopedUsers.cityId, SortOrder.DESC)
                .map { it[scopedUsers.id] }
                .let { assertEqualLists(listOf("eugene", "sergey"), it) }

            scopedUsers.stripDefaultFilter()
                .slice(scopedUsers.id)
                .selectAll()
                .orderBy(scopedUsers.name, SortOrder.ASC)
                .orderBy(scopedUsers.cityId, SortOrder.DESC)
                .map { it[scopedUsers.id] }
                .let { assertEqualLists(listOf("alex", "andrey", "eugene", "sergey", "smth"), it) }
        }
    }

    @Test
    fun orderBy03() {
        withCitiesAndUsers {
            val r = users.selectAll().orderBy(users.cityId to SortOrder.DESC, users.id to SortOrder.ASC).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if (isNullFirst()) usersWithoutCities + otherUsers
            else otherUsers + usersWithoutCities
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun testOrderBy04() {
        withCitiesAndUsers {
            (cities innerJoin users)
                .slice(cities.name, users.id.count())
                .selectAll()
                .groupBy(cities.name)
                .orderBy(cities.name)
                .toList().let { r ->
                    assertEquals(2, r.size)
                    assertEquals("Munich", r[0][cities.name])
                    assertEquals(2, r[0][users.id.count()])
                    assertEquals("St. Petersburg", r[1][cities.name])
                    assertEquals(1, r[1][users.id.count()])
                }

            (cities innerJoin scopedUsers)
                .slice(cities.name, scopedUsers.id.count())
                .selectAll()
                .groupBy(cities.name)
                .orderBy(cities.name)
                .toList().let { r ->
                    assertEquals(1, r.size)
                    assertEquals("Munich", r[0][cities.name])
                    assertEquals(2, r[0][scopedUsers.id.count()])
                }

            (cities innerJoin scopedUsers.stripDefaultFilter())
                .slice(cities.name, scopedUsers.id.count())
                .selectAll()
                .groupBy(cities.name)
                .orderBy(cities.name)
                .toList().let { r ->
                    assertEquals(2, r.size)
                    assertEquals("Munich", r[0][cities.name])
                    assertEquals(2, r[0][scopedUsers.id.count()])
                    assertEquals("St. Petersburg", r[1][cities.name])
                    assertEquals(1, r[1][scopedUsers.id.count()])
                }
        }
    }

    @Test
    fun orderBy05() {
        withCitiesAndUsers {
            val r = users.selectAll().orderBy(users.cityId to SortOrder.DESC, users.id to SortOrder.ASC).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if (isNullFirst()) usersWithoutCities + otherUsers
            else otherUsers + usersWithoutCities
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun orderBy06() {
        withCitiesAndUsers {
            val orderByExpression = users.id.substring(2, 1)
            val r = users.selectAll().orderBy(orderByExpression to SortOrder.ASC).toList()
            assertEquals(5, r.size)
            assertEquals("sergey", r[0][users.id])
            assertEquals("alex", r[1][users.id])
            assertEquals("smth", r[2][users.id])
            assertEquals("andrey", r[3][users.id])
            assertEquals("eugene", r[4][users.id])
        }
    }

    @Test
    fun testOrderByExpressions() {
        withCitiesAndUsers {
            val expression = wrapAsExpression<Int>(
                users
                    .slice(users.id.count())
                    .select {
                        cities.id eq users.cityId
                    }
            )

            val result = cities
                .selectAll()
                .orderBy(expression, SortOrder.DESC)
                .toList()

            // Munich - 2 users
            // St. Petersburg - 1 user
            // Prague - 0 users
            println(result)
        }
    }

    @Test
    fun testOrderByNullsFirst() {
        // city IDs null, user IDs sorted ascending
        val usersWithoutCities = listOf("alex", "smth")
        // city IDs sorted descending, user IDs sorted ascending
        val otherUsers = listOf("eugene", "sergey", "andrey")
        // city IDs sorted ascending, user IDs sorted ascending
        val otherUsersAsc = listOf("andrey", "eugene", "sergey")

        val cases = listOf(
            SortOrder.ASC_NULLS_FIRST to usersWithoutCities + otherUsersAsc,
            SortOrder.ASC_NULLS_LAST to otherUsersAsc + usersWithoutCities,
            SortOrder.DESC_NULLS_FIRST to usersWithoutCities + otherUsers,
            SortOrder.DESC_NULLS_LAST to otherUsers + usersWithoutCities,
        )
        withCitiesAndUsers {
            cases.forEach { (sortOrder, expected) ->
                val r = users.selectAll().orderBy(
                    users.cityId to sortOrder,
                    users.id to SortOrder.ASC
                ).toList()
                assertEquals(5, r.size)
                expected.forEachIndexed { index, e ->
                    assertEquals(e, r[index][users.id])
                }
            }
        }
    }
}
