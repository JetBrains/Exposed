package org.jetbrains.exposed.sql.exposed.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class SelectTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testSelect() = runTest {
        withCitiesAndUsers { _, users, _ ->
            users.selectAll().where { users.id.eq("andrey") }.toList().forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectAnd() = runTest {
        withCitiesAndUsers { _, users, _ ->
            users.selectAll().where { users.id.eq("andrey") and users.name.eq("Andrey") }.toList().forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }
}
