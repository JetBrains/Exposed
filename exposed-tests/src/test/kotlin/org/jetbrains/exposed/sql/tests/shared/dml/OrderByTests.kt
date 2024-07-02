package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.h2Mode
import org.junit.Test
import kotlin.test.assertContentEquals

class OrderByTests : DatabaseTestsBase() {
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
            fun assertOrdered(expected: List<Int>, order: SortOrder) {
                val ordered = NullableStrings.select(NullableStrings.id).orderBy(NullableStrings.name, order).map { it[NullableStrings.id] }
                assertContentEquals(expected, ordered)
            }
            assertOrdered(listOf(4, 2, 1, 3), SortOrder.DESC_NULLS_LAST) // c, b, a, null
            assertOrdered(listOf(1, 2, 4, 3), SortOrder.ASC_NULLS_LAST) // a, b, c, null
            assertOrdered(listOf(3, 4, 2, 1), SortOrder.DESC_NULLS_FIRST) // null, c, b, a
            assertOrdered(listOf(3, 1, 2, 4), SortOrder.ASC_NULLS_FIRST) // null, a, b, c
        }
    }

    @Test
    fun testOrderByQuery() {
        val box = object : Table("OrderByQueryBox") {
            val id = integer("id")

            override val primaryKey: PrimaryKey = PrimaryKey(id)
        }
        val coin = object : Table("OrderByQueryCoin") {
            val boxId = integer("box_id").references(box.id)
            val cost = integer("cost")
        }

        withTables(box, coin) {
            val coinBoxes = listOf(
                listOf(1, 2, 3, 4, 5), // sum: 15
                listOf(6), // sum: 6
                listOf(7, 8, 9, 10) // sum: 34
            )

            coinBoxes.forEachIndexed { index, coins ->
                box.insert { it[id] = index }
                coins.forEach { cost ->
                    coin.insert {
                        it[coin.boxId] = index
                        it[coin.cost] = cost
                    }
                }
            }

            // Variant 1
            // SELECT OrderByQueryBox.id, (SELECT SUM(OrderByQueryCoin.cost)
            //   FROM OrderByQueryCoin WHERE OrderByQueryBox.id = OrderByQueryCoin.box_id) cost_sum FROM OrderByQueryBox
            //   ORDER BY cost_sum ASC
            val expressionAlias = coin.select(coin.cost.sum()).where { coin.boxId eq box.id }.asExpression<Int>().alias("cost_sum")

            val variant1Asc = box.select(box.id, expressionAlias)
                .orderBy(expressionAlias, SortOrder.ASC)
                .map { it[box.id] }
            assertEqualLists(listOf(1, 0, 2), variant1Asc)

            val variant1Desc = box.select(box.id, expressionAlias)
                .orderBy(expressionAlias, SortOrder.DESC)
                .map { it[box.id] }
            assertEqualLists(listOf(2, 0, 1), variant1Desc)

            // Variant 2
            // SELECT OrderByQueryBox.id
            //   FROM OrderByQueryBox
            //   ORDER BY (SELECT SUM(OrderByQueryCoin.cost) FROM OrderByQueryCoin WHERE OrderByQueryBox.id = OrderByQueryCoin.box_id) DESC
            val expression = coin.select(coin.cost.sum()).where { coin.boxId eq box.id }.asExpression<Int>()

            val variant2Asc = box.select(box.id)
                .orderBy(expression, SortOrder.ASC)
                .map { it[box.id] }
            assertEqualLists(listOf(1, 0, 2), variant2Asc)

            val variant2Desc = box.select(box.id)
                .orderBy(expression, SortOrder.DESC)
                .map { it[box.id] }
            assertEqualLists(listOf(2, 0, 1), variant2Desc)
        }
    }
}
