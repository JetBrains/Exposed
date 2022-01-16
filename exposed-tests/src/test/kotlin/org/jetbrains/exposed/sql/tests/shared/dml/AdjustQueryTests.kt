package org.jetbrains.exposed.sql.tests.shared.dml

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.transactions.TransactionManager
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

                    assertThat(oldSlice, Matchers.not(containsInAnyOrder(actualSlice)))
                    assertThat(actualSlice, containsInAnyOrder(expectedSlice))
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

                    assertThat(oldSlice, Matchers.not(containsInAnyOrder(actualSlice)))
                    assertThat(actualSlice, containsInAnyOrder(expectedSlice))
                    assertScopedQueryResultValid(queryAdjusted)
                }

            (scopedUsers.stripDefaultFilter() innerJoin cities)
                .slice(scopedUsers.name)
                .select(scopedPredicate)
                .let { queryAdjusted ->
                    fun Query.sliceIt() : FieldSet = this.set.source.slice(scopedUsers.name, cities.name)

                    val oldSlice = queryAdjusted.set.fields
                    val expectedSlice = queryAdjusted.sliceIt().fields

                    queryAdjusted.adjustSlice { slice(scopedUsers.name, cities.name) }
                    val actualSlice = queryAdjusted.set.fields

                    assertThat(oldSlice, Matchers.not(containsInAnyOrder(actualSlice)))
                    assertThat(actualSlice, containsInAnyOrder(expectedSlice))
                    assertStripedScopedQueryResultValid(queryAdjusted)
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

            scopedUsers.stripDefaultFilter()
                .slice(scopedUsers.name, cities.name)
                .select(scopedPredicate)
                .let { queryAdjusted ->
                    val oldColumnSet = queryAdjusted.set.source
                    val expectedColumnSet = scopedUsers innerJoin cities
                    queryAdjusted.adjustColumnSet { innerJoin(cities) }
                    val actualColumnSet = queryAdjusted.set.source

                    assertNotEquals(oldColumnSet.repr(), actualColumnSet.repr())
                    assertEquals(expectedColumnSet.repr(), actualColumnSet.repr())
                    assertStripedScopedQueryResultValid(queryAdjusted)
                }

        }
    }

    private fun Op<Boolean>.repr(): String {
        val builder = QueryBuilder(false)
        builder.append(this)
        return builder.toString()
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

            (scopedUsers.stripDefaultFilter() innerJoin cities)
                .slice(scopedUsers.name, cities.name)
                .selectAll()
                .let { queryAdjusted ->
                    queryAdjusted.adjustWhere {
                        assertNull(this)
                        scopedPredicate
                    }
                    val actualWhere = queryAdjusted.where
                    val fullScopedPredicate = scopedPredicate

                    assertEquals(fullScopedPredicate.repr(), actualWhere!!.repr())
                    assertStripedScopedQueryResultValid(queryAdjusted)
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
                    val defaultFilter = Op.build { scopedUsers.cityId eq munichId() }
                    ((scopedPredicate.and(defaultFilter)).and(scopedPredicate.and(defaultFilter)))
                        .repr().let { expected ->
                            assertEquals(expected, actualWhere.repr())
                            assertScopedQueryResultValid(queryAdjusted)
                        }
                }

            (scopedUsers.stripDefaultFilter() innerJoin cities)
                .slice(scopedUsers.name, cities.name)
                .select { scopedPredicate }
                .let { queryAdjusted ->
                    queryAdjusted.andWhere { scopedPredicate }

                    val actualWhere = queryAdjusted.where!!
                    (scopedPredicate.and(scopedPredicate))
                        .repr().let { expected ->
                            assertEquals(expected, actualWhere.repr())
                            assertStripedScopedQueryResultValid(queryAdjusted)
                        }
                }
        }
    }

    @Test
    fun testQueryOrWhere() {
        withCitiesAndUsers {
            (users innerJoin cities)
                .slice(users.name, cities.name)
                .select { predicate }
                .let { queryAdjusted ->
                    queryAdjusted.orWhere { predicate }
                    val actualWhere = queryAdjusted.where

                    assertEquals((predicate.or(predicate)).repr(), actualWhere!!.repr())
                    assertQueryResultValid(queryAdjusted)
                }

            (scopedUsers innerJoin cities)
                .slice(scopedUsers.name, cities.name)
                .select { scopedPredicate }
                .let { queryAdjusted ->
                    queryAdjusted.orWhere { scopedPredicate }
                    val actualWhere = queryAdjusted.where

                    assertEquals(((scopedPredicate.and(scopedUsers.defaultFilter()))
                        .or(scopedPredicate)
                        .and(scopedUsers.defaultFilter()))
                        .repr(), actualWhere!!.repr())
                    assertScopedQueryResultValid(queryAdjusted)
                }
        }
    }

    @Test
    fun testAdjustQueryHaving() {
        withCitiesAndUsers {
            (cities innerJoin users)
                .slice(cities.name)
                .selectAll()
                .groupBy(cities.name)
                .let { queryAdjusted ->
                    val predicateHaving = Op.build {
                        users.id.count() eq cities.id.max()
                    }
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

            (cities innerJoin scopedUsers)
                .slice(cities.name)
                .selectAll()
                .groupBy(cities.name)
                .let { queryAdjusted ->
                    val predicateHaving = Op.build {
                        scopedUsers.id.count() eq cities.id.max()
                    }
                    queryAdjusted.adjustHaving {
                        assertNull(this)
                        predicateHaving
                    }
                    val actualHaving = queryAdjusted.having

                    assertEquals(predicateHaving.repr(), actualHaving!!.repr())
                    val r = queryAdjusted.orderBy(cities.name).toList()
                    assertEquals(1, r.size)
                    assertEquals("Munich", r[0][cities.name])
                }
        }
    }

    @Test
    fun testQueryAndHaving() {
        withCitiesAndUsers {
            val predicateHaving = Op.build {
                users.id.count() eq cities.id.max()
            }

            (cities innerJoin users)
                .slice(cities.name)
                .selectAll()
                .groupBy(cities.name)
                .having { predicateHaving }
                .let { queryAdjusted ->
                    queryAdjusted.andHaving { predicateHaving }

                    val actualHaving = queryAdjusted.having
                    assertEquals((predicateHaving.and(predicateHaving)).repr(), actualHaving!!.repr())

                    val r = queryAdjusted.orderBy(cities.name).toList()
                    assertEquals(2, r.size)
                    assertEquals("Munich", r[0][cities.name])
                    assertEquals("St. Petersburg", r[1][cities.name])
                }

            val scopedPredicateHaving = Op.build {
                scopedUsers.id.count() eq cities.id.max()
            }

            (cities innerJoin scopedUsers)
                .slice(cities.name)
                .selectAll()
                .groupBy(cities.name)
                .having { scopedPredicateHaving }
                .let { queryAdjusted ->
                    queryAdjusted.andHaving { scopedPredicateHaving }

                    val actualHaving = queryAdjusted.having
                    assertEquals((scopedPredicateHaving.and(scopedPredicateHaving)).repr(), actualHaving!!.repr())

                    val r = queryAdjusted.orderBy(cities.name).toList()
                    assertEquals(1, r.size)
                    assertEquals("Munich", r[0][cities.name])
                }
        }
    }

    @Test
    fun testQueryOrHaving() {
        withCitiesAndUsers {
            val predicateHaving = Op.build {
                users.id.count() eq cities.id.max()
            }

            (cities innerJoin users)
                .slice(cities.name)
                .selectAll()
                .groupBy(cities.name)
                .having { predicateHaving }
                .let { queryAdjusted ->
                    queryAdjusted.orHaving { predicateHaving }

                    val actualHaving = queryAdjusted.having
                    assertEquals((predicateHaving.or(predicateHaving)).repr(), actualHaving!!.repr())

                    val r = queryAdjusted.orderBy(cities.name).toList()
                    assertEquals(2, r.size)
                    assertEquals("Munich", r[0][cities.name])
                    assertEquals("St. Petersburg", r[1][cities.name])
                }

            val scopedPredicateHaving = Op.build {
                scopedUsers.id.count() eq cities.id.max()
            }

            (cities innerJoin scopedUsers)
                .slice(cities.name)
                .selectAll()
                .groupBy(cities.name)
                .having { scopedPredicateHaving }
                .let { queryAdjusted ->
                    queryAdjusted.orHaving { scopedPredicateHaving }

                    val actualHaving = queryAdjusted.having
                    assertEquals((scopedPredicateHaving.or(scopedPredicateHaving)).repr(), actualHaving!!.repr())

                    val r = queryAdjusted.orderBy(cities.name).toList()

                    println("ayo! ${r.map { it[cities.name] }}")
                    assertEquals(1, r.size)
                    assertEquals("Munich", r[0][cities.name])
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

    private fun assertStripedScopedQueryResultValid(query: Query) {
        val users = ScopedUsers
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
}
