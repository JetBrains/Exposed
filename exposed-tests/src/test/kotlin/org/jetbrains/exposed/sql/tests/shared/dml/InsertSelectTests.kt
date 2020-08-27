package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import java.math.BigDecimal

class InsertSelectTests : DatabaseTestsBase() {
    @Test
    fun testInsertSelect01() {
        withCitiesAndUsers(exclude = listOf(TestDB.ORACLE)) { cities, users, userData ->
            val substring = users.name.substring(1, 2)
            cities.insert(users.slice(substring).selectAll().orderBy(users.id).limit(2))

            val r = cities.slice(cities.name).selectAll().orderBy(cities.id, SortOrder.DESC).limit(2).toList()
            assertEquals(2, r.size)
            assertEquals("An", r[0][cities.name])
            assertEquals("Al", r[1][cities.name])
        }
    }

    @Test
    fun testInsertSelect02() {
        withCitiesAndUsers { cities, users, userData ->
            val allUserData = userData.selectAll().count()
            userData.insert(userData.slice(userData.user_id, userData.comment, intParam(42)).selectAll())

            val r = userData.select { userData.value eq 42 }.orderBy(userData.user_id).toList()
            assertEquals(allUserData, r.size.toLong())
        }
    }

    @Test
    fun testInsertSelect03() {
        withCitiesAndUsers { cities, users, userData ->
            val userCount = users.selectAll().count()
            val nullableExpression = Random() as Expression<BigDecimal?>
            users.insert(users.slice(nullableExpression.castTo<String>(VarCharColumnType()).substring(1, 10), stringParam("Foo"), intParam(1)).selectAll())
            val r = users.select { users.name eq "Foo" }.toList()
            assertEquals(userCount, r.size.toLong())
        }
    }

    @Test
    fun testInsertSelect04() {
        withCitiesAndUsers { cities, users, userData ->
            val userCount = users.selectAll().count()
            users.insert(users.slice(stringParam("Foo"), Random().castTo<String>(VarCharColumnType()).substring(1, 10)).selectAll(), columns = listOf(users.name, users.id))
            val r = users.select { users.name eq "Foo" }.toList()
            assertEquals(userCount, r.size.toLong())
        }
    }

    @Test
    fun `insert-select with same columns in a query`() {
        withCitiesAndUsers { cities, users, userData ->
            val fooParam = stringParam("Foo")
            users.insert(users.slice(fooParam, fooParam).selectAll().limit(1), columns = listOf(users.name, users.id))
            assertEquals(1, users.select { users.name eq "Foo" }.count())
        }
    }
}