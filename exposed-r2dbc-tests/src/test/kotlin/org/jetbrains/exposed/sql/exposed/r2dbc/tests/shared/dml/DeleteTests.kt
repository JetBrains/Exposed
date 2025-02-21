package org.jetbrains.exposed.sql.exposed.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.any
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.r2dbc.sql.deleteAll
import org.jetbrains.exposed.r2dbc.sql.deleteWhere
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class DeleteTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testDelete01() = runTest {
        withCitiesAndUsers { cities, users, userData ->
            userData.deleteAll()
            val userDataExists = userData.selectAll().any { true }
            assertEquals(false, userDataExists)

            val smthId = users.selectAll().where { users.name.like("%thing") }.single()[users.id]
            assertEquals("smth", smthId)

            users.deleteWhere { users.name like "%thing" }
            val hasSmth = users.selectAll().where { users.name.like("%thing") }.any { true }
            assertEquals(false, hasSmth)
        }
    }

    @Test
    fun testDeleteTableInContext() = runTest {
        withCitiesAndUsers { _, users, userData ->
            userData.deleteAll()
            val userDataExists = userData.selectAll().any { true }
            assertEquals(false, userDataExists)

            val smthId = users.selectAll().where { users.name.like("%thing") }.single()[users.id]
            assertEquals("smth", smthId)

            users.deleteWhere { name like "%thing" }
            val hasSmth = users.selectAll().where { users.name.like("%thing") }.firstOrNull()
            assertEquals(null, hasSmth)
        }
    }
}
