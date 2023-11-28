package org.jetbrains.exposed.sql.tests.shared.dml

import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AdjustQueryTests : DatabaseTestsBase() {

    @Test
    fun testAdjustQuerySlice() {
        withCitiesAndUsers { cities, users, _ ->
            val queryAdjusted = (users innerJoin cities)
                .slice(users.name)
                .select(predicate)

            fun Query.sliceIt(): FieldSet = this.set.source.slice(users.name, cities.name)
            val oldSlice = queryAdjusted.set.fields
            val expectedSlice = queryAdjusted.sliceIt().fields
            queryAdjusted.adjustSlice { slice(users.name, cities.name) }
            val actualSlice = queryAdjusted.set.fields
            fun containsInAnyOrder(list: List<*>) = Matchers.containsInAnyOrder(*list.toTypedArray())

            MatcherAssert.assertThat(oldSlice, Matchers.not(containsInAnyOrder(actualSlice)))
            MatcherAssert.assertThat(actualSlice, containsInAnyOrder(expectedSlice))
            assertQueryResultValid(queryAdjusted)
        }
    }

    @Test
    fun testAdjustQuerySliceWithEmptyListThrows() {
        withCitiesAndUsers { cities, _, _ ->
            val originalQuery = cities.slice(cities.name).selectAll()

            assertFailsWith<IllegalArgumentException> {
                originalQuery.adjustSlice { slice(emptyList()) }.toList()
            }
        }
    }

    @Test
    fun testAdjustQueryColumnSet() {
        withCitiesAndUsers { cities, users, _ ->
            val queryAdjusted = users
                .slice(users.name, cities.name)
                .select(predicate)
            val oldColumnSet = queryAdjusted.set.source
            val expectedColumnSet = users innerJoin cities
            queryAdjusted.adjustColumnSet { innerJoin(cities) }
            val actualColumnSet = queryAdjusted.set.source
            fun ColumnSet.repr(): String = QueryBuilder(false).also { this.describe(TransactionManager.current(), it) }.toString()

            assertNotEquals(oldColumnSet.repr(), actualColumnSet.repr())
            assertEquals(expectedColumnSet.repr(), actualColumnSet.repr())
            assertQueryResultValid(queryAdjusted)
        }
    }

    private fun Op<Boolean>.repr(): String {
        val builder = QueryBuilder(false)
        builder.append(this)
        return builder.toString()
    }

    @Test
    fun testAdjustQueryWhere() {
        withCitiesAndUsers { cities, users, _ ->
            val queryAdjusted = (users innerJoin cities)
                .slice(users.name, cities.name)
                .selectAll()
            queryAdjusted.adjustWhere {
                assertNull(this)
                predicate
            }
            val actualWhere = queryAdjusted.where

            assertEquals(predicate.repr(), actualWhere!!.repr())
            assertQueryResultValid(queryAdjusted)
        }
    }

    @Test
    fun testQueryAndWhere() {
        withCitiesAndUsers { cities, users, _ ->
            val queryAdjusted = (users innerJoin cities)
                .slice(users.name, cities.name)
                .select { predicate }

            queryAdjusted.andWhere {
                predicate
            }
            val actualWhere = queryAdjusted.where

            assertEquals((predicate.and(predicate)).repr(), actualWhere!!.repr())
            assertQueryResultValid(queryAdjusted)
        }
    }

    @Test
    fun testQueryOrWhere() {
        withCitiesAndUsers { cities, users, _ ->
            val queryAdjusted = (users innerJoin cities)
                .slice(users.name, cities.name)
                .select { predicate }

            queryAdjusted.orWhere {
                predicate
            }
            val actualWhere = queryAdjusted.where

            assertEquals((predicate.or(predicate)).repr(), actualWhere!!.repr())
            assertQueryResultValid(queryAdjusted)
        }
    }

    @Test
    fun testAdjustQueryHaving() {
        withCitiesAndUsers { cities, users, _ ->
            val predicateHaving = Op.build {
                DMLTestsData.Users.id.count() eq DMLTestsData.Cities.id.max()
            }

            val queryAdjusted = (cities innerJoin users)
                .slice(cities.name)
                .selectAll()
                .groupBy(cities.name)
            queryAdjusted.adjustHaving {
                assertNull(this)
                predicateHaving
            }
            val actualHaving = queryAdjusted.having

            assertEquals(predicateHaving.repr(), actualHaving!!.repr())
            val r = queryAdjusted.orderBy(cities.name).toList()
            assertEquals(2, r.size)
            assertEquals("Munich", r[0][cities.name])
            assertEquals("St. Petersburg", r[1][cities.name])
        }
    }

    @Test
    fun testQueryAndHaving() {
        withCitiesAndUsers { cities, users, _ ->
            val predicateHaving = Op.build {
                DMLTestsData.Users.id.count() eq DMLTestsData.Cities.id.max()
            }

            val queryAdjusted = (cities innerJoin users)
                .slice(cities.name)
                .selectAll()
                .groupBy(cities.name)
                .having { predicateHaving }

            queryAdjusted.andHaving {
                predicateHaving
            }

            val actualHaving = queryAdjusted.having
            assertEquals((predicateHaving.and(predicateHaving)).repr(), actualHaving!!.repr())

            val r = queryAdjusted.orderBy(cities.name).toList()
            assertEquals(2, r.size)
            assertEquals("Munich", r[0][cities.name])
            assertEquals("St. Petersburg", r[1][cities.name])
        }
    }

    @Test
    fun testQueryOrHaving() {
        withCitiesAndUsers { cities, users, _ ->
            val predicateHaving = Op.build {
                DMLTestsData.Users.id.count() eq DMLTestsData.Cities.id.max()
            }

            val queryAdjusted = (cities innerJoin users)
                .slice(cities.name)
                .selectAll()
                .groupBy(cities.name)
                .having { predicateHaving }

            queryAdjusted.orHaving {
                predicateHaving
            }

            val actualHaving = queryAdjusted.having
            assertEquals((predicateHaving.or(predicateHaving)).repr(), actualHaving!!.repr())

            val r = queryAdjusted.orderBy(cities.name).toList()
            assertEquals(2, r.size)
            assertEquals("Munich", r[0][cities.name])
            assertEquals("St. Petersburg", r[1][cities.name])
        }
    }

    private val predicate = Op.build {
        val nameCheck = (DMLTestsData.Users.id eq "andrey") or (DMLTestsData.Users.name eq "Sergey")
        val cityCheck = DMLTestsData.Users.cityId eq DMLTestsData.Cities.id
        nameCheck and cityCheck
    }

    private fun assertQueryResultValid(query: Query) {
        val users = DMLTestsData.Users
        val cities = DMLTestsData.Cities
        query.forEach { row ->
            val userName = row[users.name]
            val cityName = row[cities.name]
            when (userName) {
                "Andrey" -> assertEquals("St. Petersburg", cityName)
                "Sergey" -> assertEquals("Munich", cityName)
                else -> error("Unexpected user $userName")
            }
        }
    }
}
