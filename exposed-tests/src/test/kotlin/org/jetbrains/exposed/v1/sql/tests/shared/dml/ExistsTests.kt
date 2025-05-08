package org.jetbrains.exposed.v1.sql.tests.shared.dml

import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.SqlExpressionBuilder.case
import org.jetbrains.exposed.v1.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.currentDialectTest
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.sql.vendors.OracleDialect
import org.jetbrains.exposed.v1.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.sql.vendors.currentDialect
import org.junit.Test

class ExistsTests : DatabaseTestsBase() {
    @Test
    fun testExists01() {
        withCitiesAndUsers { _, users, userData ->
            val r = users.selectAll().where {
                org.jetbrains.exposed.v1.sql.exists(
                    userData.selectAll().where((userData.user_id eq users.id) and (userData.comment like "%here%"))
                )
            }.toList()
            assertEquals(1, r.size)
            assertEquals("Something", r[0][users.name])
        }
    }

    @Test
    fun testExistsInASlice() {
        withCitiesAndUsers { _, users, userData ->
            var exists: Expression<Boolean> = org.jetbrains.exposed.v1.sql.exists(
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
                    org.jetbrains.exposed.v1.sql.exists(
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
                org.jetbrains.exposed.v1.sql.exists(
                    userData.selectAll().where((userData.user_id eq users.id) and (userData.comment like "%here%"))
                ) or
                    org.jetbrains.exposed.v1.sql.exists(
                        userData.selectAll()
                            .where((userData.user_id eq users.id) and (userData.comment like "%Sergey"))
                    )
            }
                .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }
}
