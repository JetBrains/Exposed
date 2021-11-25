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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AdjustQueryTests : DatabaseTestsBase() {

    @Test
    fun testAdjustQuerySlice() {
        withCitiesAndUsers {
            fun containsInAnyOrder(list: List<*>) = Matchers.containsInAnyOrder(*list.toTypedArray())

            (users innerJoin cities)
                .slice(users.name)
                .select(predicate)
                .let { queryAdjusted ->
                    fun Query.sliceIt() : FieldSet = this.set.source.slice(users.name, cities.name)

                    val oldSlice = queryAdjusted.set.fields
                    val expectedSlice = queryAdjusted.sliceIt().fields
                    queryAdjusted.adjustSlice { slice(users.name, cities.name) }
                    val actualSlice = queryAdjusted.set.fields

                    Assert.assertThat(oldSlice, Matchers.not(containsInAnyOrder(actualSlice)))
                    Assert.assertThat(actualSlice, containsInAnyOrder(expectedSlice))
                    assertQueryResultValid(queryAdjusted)
                }

            (scopedUsers innerJoin cities)
                .slice(scopedUsers.name)
                .select(scopedPredicate)
                .let { queryAdjusted ->
                    fun Query.sliceIt() : FieldSet = this.set.source.slice(scopedUsers.name, cities.name)

                    val oldSlice = queryAdjusted.set.fields
                    val expectedSlice = queryAdjusted.sliceIt().fields

                    queryAdjusted.adjustSlice { slice(scopedUsers.name, cities.name) }
                    val actualSlice = queryAdjusted.set.fields

                    Assert.assertThat(oldSlice, Matchers.not(containsInAnyOrder(actualSlice)))
                    Assert.assertThat(actualSlice, containsInAnyOrder(expectedSlice))
                    assertScopedQueryResultValid(queryAdjusted)
                }
        }
    }

    @Test
    fun testAdjustQueryColumnSet() {
        withCitiesAndUsers {
            fun ColumnSet.repr() : String = QueryBuilder(false)
                .also { this.describe(TransactionManager.current(), it) }
                .toString()

            users.slice(users.name, cities.name)
                .select(predicate)
                .let { queryAdjusted ->
                    val oldColumnSet = queryAdjusted.set.source
                    val expectedColumnSet = users innerJoin cities
                    queryAdjusted.adjustColumnSet { innerJoin(cities) }
                    val actualColumnSet = queryAdjusted.set.source

                    assertNotEquals(oldColumnSet.repr(), actualColumnSet.repr())
                    assertEquals(expectedColumnSet.repr(), actualColumnSet.repr())
                    assertQueryResultValid(queryAdjusted)
                }

            scopedUsers.slice(scopedUsers.name, cities.name)
                .select(scopedPredicate)
                .let { queryAdjusted ->
                    val oldColumnSet = queryAdjusted.set.source
                    val expectedColumnSet = scopedUsers innerJoin cities
                    queryAdjusted.adjustColumnSet { innerJoin(cities) }
                    val actualColumnSet = queryAdjusted.set.source

                    assertNotEquals(oldColumnSet.repr(), actualColumnSet.repr())
                    assertEquals(expectedColumnSet.repr(), actualColumnSet.repr())
                    assertScopedQueryResultValid(queryAdjusted)
                }

        }
    }

    @Test
    fun testAdjustQueryWhere() {
        withCitiesAndUsers {
            fun Op<Boolean>.repr(): String {
                val builder = QueryBuilder(false)
                builder.append(this)
                return builder.toString()
            }

            (users innerJoin cities)
                .slice(users.name, cities.name)
                .selectAll()
                .let { queryAdjusted ->
                    queryAdjusted.adjustWhere {
                        assertNull(this)
                        predicate
                    }
                    val actualWhere = queryAdjusted.where

                    assertEquals(predicate.repr(), actualWhere!!.repr())
                    assertQueryResultValid(queryAdjusted)
                }

            (scopedUsers innerJoin cities)
                .slice(scopedUsers.name, cities.name)
                .selectAll()
                .let { queryAdjusted ->
                    queryAdjusted.adjustWhere {
                        assertNotNull(this)
                        scopedPredicate
                    }
                    val actualWhere = queryAdjusted.where
                    val fullScopedPredicate = scopedPredicate.and(Op.build { scopedUsers.cityId eq munichId() })

                    assertEquals(fullScopedPredicate.repr(), actualWhere!!.repr())
                    assertScopedQueryResultValid(queryAdjusted)
                }
        }
    }

    @Test
    fun testQueryAndWhere() {
        fun Op<Boolean>.repr(): String {
            val builder = QueryBuilder(false)
            builder.append(this)
            return builder.toString()
        }

        withCitiesAndUsers {
            (users innerJoin cities)
                .slice(users.name, cities.name)
                .select { predicate }
                .let { queryAdjusted ->
                    queryAdjusted.andWhere { predicate }

                    val actualWhere = queryAdjusted.where

                    assertEquals((predicate.and(predicate)).repr(), actualWhere!!.repr())
                    assertQueryResultValid(queryAdjusted)
                }

            (scopedUsers innerJoin cities)
                .slice(scopedUsers.name, cities.name)
                .select { scopedPredicate }
                .let { queryAdjusted ->
                    queryAdjusted.andWhere { scopedPredicate }

                    val actualWhere = queryAdjusted.where!!
                    val defaultScope = Op.build { scopedUsers.cityId eq munichId() }
                    ((scopedPredicate.and(defaultScope)).and(scopedPredicate.and(defaultScope)))
                        .repr().let { expected ->
                            assertEquals(expected, actualWhere.repr())
                            assertScopedQueryResultValid(queryAdjusted)
                        }
                }
        }
    }

    private val predicate = Op.build {
        val nameCheck = (Users.id eq "andrey") or (Users.name eq "Sergey")
        val cityCheck = Users.cityId eq Cities.id
        nameCheck and cityCheck
    }

    private val scopedPredicate = Op.build {
        ((ScopedUsers.id eq "andrey") or
        (ScopedUsers.name eq "Sergey")) and
        (ScopedUsers.cityId eq Cities.id)
    }

    private fun assertQueryResultValid(query: Query) {
        val users = Users
        val cities = Cities
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

    private fun assertScopedQueryResultValid(query: Query) {
        val users = ScopedUsers
        val cities = Cities
        query.forEach { row ->
            val userName = row[users.name]
            val cityName = row[cities.name]
            when (userName) {
                "Sergey" -> assertEquals("Munich", cityName)
                else -> error("Unexpected user $userName")
            }
        }
    }
}
