package org.jetbrains.exposed.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.SqlExpressionBuilder.case
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.booleanLiteral
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test

class ExistsTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testExists01() {
        withCitiesAndUsers { _, users, userData ->
            val r = users.selectAll().where {
                exists(userData.selectAll().where((userData.user_id eq users.id) and (userData.comment like "%here%")))
            }.toList()
            assertEquals(1, r.size)
            assertEquals("Something", r[0][users.name])
        }
    }

    @Test
    fun testExistsInASlice() {
        withCitiesAndUsers { _, users, userData ->
            var exists: Expression<Boolean> = exists(
                userData.selectAll().where((userData.user_id eq users.id) and (userData.comment like "%here%"))
            )
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                exists = case().When(exists, booleanLiteral(true)).Else(booleanLiteral(false))
            }
            val r1 = users.select(exists).first()
            assertEquals(false, r1[exists])

            var notExists: Expression<Boolean> = notExists(
                userData.selectAll().where((userData.user_id eq users.id) and (userData.comment like "%here%"))
            )
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                notExists = case().When(notExists, booleanLiteral(true)).Else(booleanLiteral(false))
            }
            val r2 = users.select(notExists).first()
            assertEquals(true, r2[notExists])
        }
    }

    @Test
    fun testExists02() {
        withCitiesAndUsers { _, users, userData ->
            val r = users
                .selectAll()
                .where {
                    exists(
                        userData.selectAll().where(
                            (userData.user_id eq users.id) and ((userData.comment like "%here%") or (userData.comment like "%Sergey"))
                        )
                    )
                }
                .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }

    @Test
    fun testExists03() {
        withCitiesAndUsers { _, users, userData ->
            val r = users.selectAll().where {
                exists(userData.selectAll().where((userData.user_id eq users.id) and (userData.comment like "%here%"))) or
                    exists(userData.selectAll().where((userData.user_id eq users.id) and (userData.comment like "%Sergey")))
            }
                .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }
}
