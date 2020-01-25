package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.junit.Test

class DeleteTests : DatabaseTestsBase() {
    @Test
    fun testDelete01() {
        withCitiesAndUsers { cities, users, userData ->
            userData.deleteAll()
            val userDataExists = userData.selectAll().any()
            assertEquals(false, userDataExists)

            val smthId = users.slice(users.id).select { users.name.like("%thing") }.single()[users.id]
            assertEquals("smth", smthId)

            users.deleteWhere { users.name like "%thing" }
            val hasSmth = users.slice(users.id).select { users.name.like("%thing") }.any()
            assertEquals(false, hasSmth)
        }
    }

    @Test
    fun testDeleteWithLimitAndOffset01() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLITE, TestDB.POSTGRESQL, TestDB.ORACLE)) { cities, users, userData ->
            userData.deleteWhere(limit = 1) { userData.value eq 20 }
            userData.slice(userData.user_id, userData.value).select { userData.value eq 20 }.let {
                assertEquals(1, it.count())
                val expected = if (currentDialectTest is H2Dialect) "smth" else "eugene"
                assertEquals(expected, it.single()[userData.user_id])
            }
        }
    }
}