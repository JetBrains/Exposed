package org.jetbrains.exposed.sql.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import java.math.BigDecimal

class InsertSelectTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testInsertSelect01() {
        withCitiesAndUsers(exclude = listOf(TestDB.ORACLE)) { cities, users, _ ->
            val nextVal = cities.id.autoIncColumnType?.nextValExpression
            val substring = users.name.substring(1, 2)
            val slice = listOfNotNull(nextVal, substring)
            cities.insert(users.select(slice).orderBy(users.id).limit(2))

            val r = cities.select(cities.name).orderBy(cities.id, SortOrder.DESC).limit(2).toList()
            assertEquals(2, r.size)
            assertEquals("An", r[0][cities.name])
            assertEquals("Al", r[1][cities.name])
        }
    }

    @Test
    fun testInsertSelect02() {
        withCitiesAndUsers { _, _, userData ->
            val allUserData = userData.selectAll().count()
            userData.insert(userData.select(userData.user_id, userData.comment, intParam(42)))

            val r = userData.selectAll().where { userData.value eq 42 }.orderBy(userData.user_id).toList()
            assertEquals(allUserData, r.size.toLong())
        }
    }

    @Test
    fun testInsertSelect03() {
        withCitiesAndUsers { _, users, _ ->
            val userCount = users.selectAll().count()
            val nullableExpression = Random() as Expression<BigDecimal?>
            users.insert(
                users.select(
                    nullableExpression.castTo(VarCharColumnType()).substring(1, 10),
                    stringParam("Foo"), intParam(1), intLiteral(0)
                )
            )
            val r = users.selectAll().where { users.name eq "Foo" }.toList()
            assertEquals(userCount, r.size.toLong())
        }
    }

    @Test
    fun testInsertSelect04() {
        withCitiesAndUsers { _, users, _ ->
            val userCount = users.selectAll().count()
            users.insert(
                users.select(stringParam("Foo"), Random().castTo(VarCharColumnType()).substring(1, 10)),
                columns = listOf(users.name, users.id)
            )
            val r = users.selectAll().where { users.name eq "Foo" }.toList()
            assertEquals(userCount, r.size.toLong())
        }
    }

    @Test
    fun `insert-select with same columns in a query`() {
        withCitiesAndUsers { _, users, _ ->
            val fooParam = stringParam("Foo")
            users.insert(users.select(fooParam, fooParam).limit(1), columns = listOf(users.name, users.id))
            assertEquals(1, users.selectAll().where { users.name eq "Foo" }.count())
        }
    }
}
