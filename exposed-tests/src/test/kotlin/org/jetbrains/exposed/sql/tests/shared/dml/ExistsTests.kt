package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.case
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
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
        withCitiesAndUsers { _, users, userData ->
            var exists: Expression<Boolean> = exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%")))
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                exists = case().When(exists, booleanLiteral(true)).Else(booleanLiteral(false))
            }
            val r1 = users.slice(exists).selectAll().first()
            assertEquals(false, r1[exists])

            var notExists: Expression<Boolean> = notExists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%")))
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                notExists = case().When(notExists, booleanLiteral(true)).Else(booleanLiteral(false))
            }
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
