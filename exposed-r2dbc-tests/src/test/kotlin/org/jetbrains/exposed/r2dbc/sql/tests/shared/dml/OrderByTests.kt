package org.jetbrains.exposed.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.r2dbc.sql.tests.currentDialectTest
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.substring
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.h2Mode
import org.jetbrains.exposed.sql.wrapAsExpression
import org.junit.Test

class OrderByTests : R2dbcDatabaseTestsBase() {
    @Test
    fun orderBy01() {
        withCitiesAndUsers { _, users, _ ->
            val r = users.selectAll().orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals("alex", r[0][users.id])
            assertEquals("andrey", r[1][users.id])
            assertEquals("eugene", r[2][users.id])
            assertEquals("sergey", r[3][users.id])
            assertEquals("smth", r[4][users.id])
        }
    }

    private fun isNullFirst() = when (currentDialectTest) {
        is OracleDialect, is PostgreSQLDialect -> true
        is H2Dialect -> currentDialectTest.h2Mode in listOf(H2CompatibilityMode.PostgreSQL, H2CompatibilityMode.Oracle)
        else -> false
    }

    @Test
    fun orderBy02() {
        withCitiesAndUsers { _, users, _ ->
            val r = users.selectAll().orderBy(users.cityId, SortOrder.DESC).orderBy(users.id).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if (isNullFirst()) {
                usersWithoutCities + otherUsers
            } else {
                otherUsers + usersWithoutCities
            }
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun orderBy03() {
        withCitiesAndUsers { _, users, _ ->
            val r = users.selectAll().orderBy(users.cityId to SortOrder.DESC, users.id to SortOrder.ASC).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if (isNullFirst()) {
                usersWithoutCities + otherUsers
            } else {
                otherUsers + usersWithoutCities
            }
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun testOrderBy04() {
        withCitiesAndUsers { cities, users, _ ->
            val r = (cities innerJoin users).select(
                cities.name,
                users.id.count()
            ).groupBy(cities.name).orderBy(cities.name).toList()
            assertEquals(2, r.size)
            assertEquals("Munich", r[0][cities.name])
            assertEquals(2, r[0][users.id.count()])
            assertEquals("St. Petersburg", r[1][cities.name])
            assertEquals(1, r[1][users.id.count()])
        }
    }

    @Test
    fun orderBy05() {
        withCitiesAndUsers { _, users, _ ->
            val r = users.selectAll().orderBy(users.cityId to SortOrder.DESC, users.id to SortOrder.ASC).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if (isNullFirst()) {
                usersWithoutCities + otherUsers
            } else {
                otherUsers + usersWithoutCities
            }
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun orderBy06() {
        withCitiesAndUsers { _, users, _ ->
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
        withCitiesAndUsers { cities, users, _ ->
            val expression = wrapAsExpression<Int>(
                users
                    .select(users.id.count())
                    .where { cities.id eq users.cityId }
            )

            val result = cities
                .selectAll()
                .orderBy(expression, SortOrder.DESC)
                .map { it[cities.name] }

            // Munich - 2 users
            // St. Petersburg - 1 user
            // Prague - 0 users
            assertEqualLists(result, listOf("Munich", "St. Petersburg", "Prague"))
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
        withCitiesAndUsers { _, users, _ ->
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

    object NullableStrings : Table() {
        val id: Column<Int> = integer("id").autoIncrement()
        val name: Column<String?> = varchar("name", 50).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testNullableStringOrdering() {
        withTables(NullableStrings) {
            NullableStrings.insert {
                it[name] = "a"
            }
            NullableStrings.insert {
                it[name] = "b"
            }
            NullableStrings.insert {
                it[name] = null
            }
            NullableStrings.insert {
                it[name] = "c"
            }
            suspend fun assertOrdered(expected: List<Int>, order: SortOrder) {
                val ordered = NullableStrings.select(NullableStrings.id).orderBy(NullableStrings.name, order).map { it[NullableStrings.id] }
                assertEqualLists(expected, ordered)
            }
            assertOrdered(listOf(4, 2, 1, 3), SortOrder.DESC_NULLS_LAST) // c, b, a, null
            assertOrdered(listOf(1, 2, 4, 3), SortOrder.ASC_NULLS_LAST) // a, b, c, null
            assertOrdered(listOf(3, 4, 2, 1), SortOrder.DESC_NULLS_FIRST) // null, c, b, a
            assertOrdered(listOf(3, 1, 2, 4), SortOrder.ASC_NULLS_FIRST) // null, a, b, c
        }
    }
}
