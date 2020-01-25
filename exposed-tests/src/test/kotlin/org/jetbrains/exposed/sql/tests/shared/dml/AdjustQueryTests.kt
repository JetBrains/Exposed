package org.jetbrains.exposed.sql.tests.shared.dml

import org.hamcrest.Matchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Assert
import org.junit.Test
import kotlin.test.assertEquals
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

            Assert.assertThat(oldSlice, Matchers.not(containsInAnyOrder(actualSlice)))
            Assert.assertThat(actualSlice, containsInAnyOrder(expectedSlice))
            assertQueryResultValid(queryAdjusted)
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
            fun ColumnSet.repr(): String = QueryBuilder(false).also { this.describe(TransactionManager.current(), it ) }.toString()

            assertNotEquals(oldColumnSet.repr(), actualColumnSet.repr())
            assertEquals(expectedColumnSet.repr(), actualColumnSet.repr())
            assertQueryResultValid(queryAdjusted)
        }
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
            fun Op<Boolean>.repr(): String {
                val builder = QueryBuilder(false)
                builder.append(this)
                return builder.toString()
            }

            assertEquals(predicate.repr(), actualWhere!!.repr())
            assertQueryResultValid(queryAdjusted)
        }
    }

    @Test
    fun testQueryAndWhere() {
        withCitiesAndUsers { cities, users, _ ->
            val queryAdjusted = (users innerJoin cities)
                .slice(users.name, cities.name)
                .select{ predicate }

            queryAdjusted.andWhere {
                predicate
            }
            val actualWhere = queryAdjusted.where
            fun Op<Boolean>.repr(): String {
                val builder = QueryBuilder(false)
                builder.append(this)
                return builder.toString()
            }

            assertEquals((predicate.and(predicate)).repr(), actualWhere!!.repr())
            assertQueryResultValid(queryAdjusted)
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