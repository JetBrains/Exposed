package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class ExistsTests : DatabaseTestsBase() {
    @Test
    fun testExists01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select { exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%"))) }.toList()
            assertEquals(1, r.size)
            assertEquals("Something", r[0][users.name])
        }
    }

    @Test
    fun testExistsInASlice() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLSERVER)) { _, users, userData ->
            val exists = exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%")))
            val r1 = users.slice(exists).selectAll().first()
            assertEquals(false, r1[exists])

            val notExists = notExists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%")))
            val r2 = users.slice(notExists).selectAll().first()
            assertEquals(true, r2[notExists])
        }
    }

    @Test
    fun testExists02() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select { exists(userData.select((userData.user_id eq users.id) and ((userData.comment like "%here%") or (userData.comment like "%Sergey")))) }
                .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }

    @Test
    fun testExists03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select {
                exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%"))) or
                    exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%Sergey")))
            }
                .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }
}
