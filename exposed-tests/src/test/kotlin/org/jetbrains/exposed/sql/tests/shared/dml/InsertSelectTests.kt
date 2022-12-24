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
        withCitiesAndUsers(exclude = listOf(TestDB.ORACLE)) {
            val nextVal = cities.id.autoIncColumnType?.nextValExpression
            val substring = users.name.substring(1, 2)
            val slice = listOfNotNull(nextVal, substring)
            cities.insert(users.slice(slice).selectAll().orderBy(users.id).limit(2))

            cities.slice(cities.name)
                .selectAll().orderBy(cities.id, SortOrder.DESC)
                .limit(2).toList().let { r ->
                    assertEquals(2, r.size)
                    assertEquals("An", r[0][cities.name])
                    assertEquals("Al", r[1][cities.name])
                }

            val scopedSubString = scopedUsers.name.substring(1, 2)
            val scopedSlice = listOfNotNull(nextVal, scopedSubString)
            cities.insert(scopedUsers.slice(scopedSlice).selectAll().orderBy(scopedUsers.id).limit(2))

            cities.slice(cities.name)
                .selectAll().orderBy(cities.id, SortOrder.DESC)
                .limit(2).toList()
                .map { it[cities.name] }
                .sortedByDescending { it }
                .let { reloadedCities ->
                    println("aye! ${reloadedCities}")
                    assertEquals(2, reloadedCities.size)
                    assertEquals("Se", reloadedCities[0])
                    assertEquals("Eu", reloadedCities[1])
                }
        }
    }

    @Test
    fun testInsertSelect02() {
        withCitiesAndUsers {
            val allUserData = userData.selectAll().count()
            userData.insert(
                userData.slice(
                    userData.user_id,
                    userData.comment,
                    intParam(42)
                ).selectAll()
            )

            userData.select { userData.value eq 42 }
                .orderBy(userData.user_id).toList()
                .let { r -> assertEquals(allUserData, r.size.toLong()) }

        }
    }

    @Test
    fun testInsertSelect03() {
        withCitiesAndUsers {
            val userCount = users.selectAll().count()
            val nullableExpression = Random() as Expression<BigDecimal?>
            users.insert(users.slice(nullableExpression.castTo<String>(VarCharColumnType()).substring(1, 10), stringParam("Foo"), intParam(1), intLiteral(0)).selectAll())
            val r = users.select { users.name eq "Foo" }.toList()
            assertEquals(userCount, r.size.toLong())
        }
    }

    @Test
    fun testInsertSelect04() {
        withCitiesAndUsers {
            val userCount = users.selectAll().count()
            users.insert(users.slice(stringParam("Foo"), Random().castTo<String>(VarCharColumnType()).substring(1, 10)).selectAll(), columns = listOf(users.name, users.id))
            val r = users.select { users.name eq "Foo" }.toList()
            assertEquals(userCount, r.size.toLong())
        }
    }

    @Test
    fun `insert-select with same columns in a query`() {
        withCitiesAndUsers {
            val fooParam = stringParam("Foo")
            users.insert(users.slice(fooParam, fooParam).selectAll().limit(1), columns = listOf(users.name, users.id))
            assertEquals(1, users.select { users.name eq "Foo" }.count())
        }
    }
}
