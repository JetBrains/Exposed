package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.junit.Test

class OrderByTests : DatabaseTestsBase() {
    @Test
    fun orderBy01() {
        withCitiesAndUsers { cities, users, userData ->
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
        else -> false
    }

    @Test
    fun orderBy02() {
        withCitiesAndUsers { _, users, _ ->
            val r = users.selectAll().orderBy(users.cityId, SortOrder.DESC).orderBy(users.id).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if(isNullFirst()) usersWithoutCities + otherUsers
            else otherUsers + usersWithoutCities
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun orderBy03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy(users.cityId to SortOrder.DESC, users.id to SortOrder.ASC).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if(isNullFirst()) usersWithoutCities + otherUsers
            else otherUsers + usersWithoutCities
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun testOrderBy04() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users).slice(cities.name, users.id.count()).selectAll().groupBy(cities.name).orderBy(cities.name).toList()
            assertEquals(2, r.size)
            assertEquals("Munich", r[0][cities.name])
            assertEquals(2, r[0][users.id.count()])
            assertEquals("St. Petersburg", r[1][cities.name])
            assertEquals(1, r[1][users.id.count()])
        }
    }

    @Test
    fun orderBy05() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy(users.cityId to SortOrder.DESC, users.id to SortOrder.ASC).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if(isNullFirst()) usersWithoutCities + otherUsers
            else otherUsers + usersWithoutCities
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun orderBy06() {
        withCitiesAndUsers { cities, users, userData ->
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
        withCitiesAndUsers { cities, users, userData ->
            val expression = wrapAsExpression<Int>(users
                .slice(users.id.count())
                .select {
                    cities.id eq users.cityId
                })

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
}