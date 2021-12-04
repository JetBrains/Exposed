package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.case
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFalse
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.entities.`Table id not in Record Test issue 1341`.NamesTable.first
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import org.testcontainers.shaded.org.bouncycastle.asn1.x500.style.RFC4519Style.c

class ExistsTests : DatabaseTestsBase() {
    @Test
    fun testExists01() {
        withCitiesAndUsers {
            users.select {
                    exists(userData.select((userData.user_id eq users.id)
                                               and (userData.comment like "%here%")))
                }.toList()
                .let { r ->
                    assertEquals(1, r.size)
                    assertEquals("Something", r[0][users.name])
                }

            scopedUsers.select {
                exists(scopedUserData.select{ scopedUserData.userId eq scopedUsers.id })
            }.toList().let { r ->
                assertEquals(1, r.size)
                assertEquals("Sergey", r[0][scopedUsers.name])
            }

            scopedUsers.select {
                exists(scopedUserData
                           .stripDefaultScope()
                           .select{ scopedUserData.userId eq scopedUsers.id })
            }.map { it[scopedUsers.name] }
            .let {
                assertEquals(2, it.size)
                assertTrue("Sergey" in it)
                assertTrue("Eugene" in it)
            }

            scopedUsers.stripDefaultScope()
                .select {
                    exists(scopedUserData
                           .stripDefaultScope()
                           .select{ scopedUserData.userId eq scopedUsers.id })
                }.map { it[scopedUsers.name] }
                .let {
                    assertEquals(3, it.size)
                    assertTrue("Sergey" in it)
                    assertTrue("Eugene" in it)
                    assertTrue("Something" in it)
                }
        }
    }

    @Test
    fun testExistsInASlice() {
        withCitiesAndUsers {
            // Exists and no default scope set.
            var exists: Expression<Boolean> = exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%")))
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                exists = case().When(exists, booleanLiteral(true)).Else(booleanLiteral(false))
            }
            users.slice(exists).selectAll()
                .also { assertFalse(it.first()[exists]) }
                .also { rows -> assertEquals(1, rows.filter { it[exists] }.size) }

            // Not exists & no default scope
            var notExists: Expression<Boolean> = notExists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%")))
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                notExists = case().When(notExists, booleanLiteral(true)).Else(booleanLiteral(false))
            }

            val r2 = users.slice(notExists).selectAll().first()
            assertEquals(true, r2[notExists])


            // Exists with a default scope and some data in scope of the exists query
            var scopedExists : Expression<Boolean> = exists(scopedUserData.select((scopedUserData.userId eq scopedUsers.id)))
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                scopedExists = case()
                    .When(scopedExists, booleanLiteral(true))
                    .Else(booleanLiteral(false))
            }
            scopedUsers.slice(scopedExists)
                .selectAll().also { rows ->
                    assertEquals(1, rows.filter { it[scopedExists] }.size)
                }

            // Exists with a default scope and some data in scope of the exists query
            // Right table's default scope is striped
            scopedExists = exists(scopedUserData.stripDefaultScope().select((scopedUserData.userId eq scopedUsers.id)))
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                scopedExists = case()
                    .When(scopedExists, booleanLiteral(true))
                    .Else(booleanLiteral(false))
            }
            scopedUsers.slice(scopedExists)
                .selectAll().also { rows ->
                    assertEquals(2, rows.filter { it[scopedExists] }.size)
                }
            scopedUsers.stripDefaultScope()
                .slice(scopedExists)
                .selectAll().also { rows ->
                    assertEquals(3, rows.filter { it[scopedExists] }.size)
                }

            // Exists with a default scope and no data in scope of the exists query
            var scopedExists2 : Expression<Boolean> = exists(userData.select((userData.user_id eq scopedUsers.id) and (userData.comment like "%here%")))
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                scopedExists2 = case().When(scopedExists2, booleanLiteral(true))
                    .Else(booleanLiteral(false))
            }
            scopedUsers
                .slice(scopedExists2).selectAll()
                .also { rows -> assertEquals(0, rows.filter { it[scopedExists2] }.size) }


            // Not Exists with a default scope
            var scopedExists3 : Expression<Boolean> = notExists(scopedUserData.select((scopedUserData.userId eq scopedUsers.id)))
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                scopedExists3 = case()
                    .When(scopedExists3, booleanLiteral(true))
                    .Else(booleanLiteral(false))
            }
            scopedUsers
                .slice(scopedExists3).selectAll()
                .also { rows -> assertEquals(1, rows.filter { it[scopedExists3] }.size) }


            // Not Exists with the default scope on the right table striped
            scopedExists3 = notExists(scopedUserData.stripDefaultScope().select((scopedUserData.userId eq scopedUsers.id)))
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                scopedExists3 = case()
                    .When(scopedExists3, booleanLiteral(true))
                    .Else(booleanLiteral(false))
            }
            scopedUsers.slice(scopedExists3).selectAll()
                .also { rows -> assertEquals(0, rows.filter { it[scopedExists3] }.size) }
            scopedUsers.stripDefaultScope()
                .slice(scopedExists3).selectAll()
                .also { rows -> assertEquals(2, rows.filter { it[scopedExists3] }.size) }


            // Not exists with a default scope and no data in scope of the exists query
            var scopedExists4 : Expression<Boolean> = notExists(userData.select((userData.user_id eq scopedUsers.id) and (userData.comment like "%here%")))
            if (currentDialectTest is OracleDialect || currentDialect is SQLServerDialect) {
                scopedExists4 = case().When(scopedExists4, booleanLiteral(true))
                    .Else(booleanLiteral(false))
            }
            scopedUsers
                .slice(scopedExists4).selectAll()
                .also { rows -> assertEquals(2, rows.filter { it[scopedExists4] }.size) }
        }
    }

    @Test
    fun testExists02() {
        withCitiesAndUsers {
            users.select {
                    exists(userData.select(
                        (userData.user_id eq users.id)
                            and ((userData.comment like "%here%")
                            or (userData.comment like "%Sergey"))))
                }.orderBy(users.id)
                .toList()
                .let { r ->
                    assertEquals(2, r.size)
                    assertEquals("Sergey", r[0][users.name])
                    assertEquals("Something", r[1][users.name])
                }

            scopedUsers.select {
                    exists(scopedUserData.select(
                        (scopedUserData.userId eq scopedUsers.id)
                            and ((scopedUserData.comment like "%here%")
                            or (scopedUserData.comment like "%Sergey"))))
                }.orderBy(scopedUsers.id)
                .map { it[scopedUsers.name] }
                .let { r ->
                    assertEquals(1, r.size)
                    assertEquals("Sergey", r[0])
                }

            // Stripping the right table's default scope
            scopedUsers.select {
                exists(scopedUserData.stripDefaultScope()
                           .select((scopedUserData.userId eq scopedUsers.id)
                                       and ((scopedUserData.comment like "%here%")
                               or (scopedUserData.comment like "%Sergey"))))
            }.orderBy(scopedUsers.id)
                .map { it[scopedUsers.name] }
                .let { r ->
                    assertEquals(1, r.size)
                    assertEquals("Sergey", r[0])
                }

            // Stripping the right table's default scope only
            scopedUsers.stripDefaultScope().select {
                exists(scopedUserData.select(
                    (scopedUserData.userId eq scopedUsers.id)
                        and ((scopedUserData.comment like "%here%")
                        or (scopedUserData.comment like "%Sergey"))))
            }.orderBy(scopedUsers.id)
                .map { it[scopedUsers.name] }
                .let { r ->
                    assertEquals(1, r.size)
                    assertEquals("Sergey", r[0])
                }

            // Stripping both scopes
            scopedUsers.stripDefaultScope().select {
                exists(scopedUserData.stripDefaultScope()
                           .select(
                               (scopedUserData.userId eq scopedUsers.id)
                                   and ((scopedUserData.comment like "%here%")
                                   or (scopedUserData.comment like "%Sergey"))))
            }.orderBy(scopedUsers.id)
                .map { it[scopedUsers.name] }
                .let {
                    assertEquals(2, it.size)
                    assertTrue("Sergey" in it && "Something" in it)
                }
        }
    }

    @Test
    fun testExists03() {
        withCitiesAndUsers {
            users.select {
                    exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%"))) or
                    exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%Sergey")))
                }.orderBy(users.id).toList()
                .let { r ->
                    assertEquals(2, r.size)
                    assertEquals("Sergey", r[0][users.name])
                    assertEquals("Something", r[1][users.name])
                }

            scopedUsers.select {
                    exists(scopedUserData.select((scopedUserData.userId eq scopedUsers.id) and (scopedUserData.comment like "%here%"))) or
                    exists(scopedUserData.select((scopedUserData.userId eq scopedUsers.id) and (scopedUserData.comment like "%Sergey")))
                }.orderBy(scopedUsers.id).toList()
                .map { it[scopedUsers.name] }
                .let {
                    assertEquals(1, it.size)
                    assertEquals("Sergey", it[0])
                }

            // Stripping on of the right table's default scopes
            scopedUsers.select {
                exists(scopedUserData.stripDefaultScope().select((scopedUserData.userId eq scopedUsers.id) and (scopedUserData.comment like "%here%"))) or
                    exists(scopedUserData.select((scopedUserData.userId eq scopedUsers.id) and (scopedUserData.comment like "%Sergey")))
            }.orderBy(scopedUsers.id).toList()
                .map { it[scopedUsers.name] }
                .let {
                    assertEquals(1, it.size)
                    assertEquals("Sergey", it[0])
                }

            // Stripping the left table's default scope only
            scopedUsers.stripDefaultScope()
                .select {
                    exists(scopedUserData.select((scopedUserData.userId eq scopedUsers.id) and (scopedUserData.comment like "%here%"))) or
                    exists(scopedUserData.select((scopedUserData.userId eq scopedUsers.id) and (scopedUserData.comment like "%Sergey")))
            }.orderBy(scopedUsers.id).toList()
                .map { it[scopedUsers.name] }
                .let {
                    assertEquals(1, it.size)
                    assertEquals("Sergey", it[0])
                }

            // Stripping the other right table's default scope
            scopedUsers.select {
                exists(scopedUserData.select((scopedUserData.userId eq scopedUsers.id) and (scopedUserData.comment like "%here%"))) or
                    exists(scopedUserData.stripDefaultScope().select((scopedUserData.userId eq scopedUsers.id) and (scopedUserData.comment like "%Sergey")))
            }.orderBy(scopedUsers.id)
            .map { it[scopedUsers.name] }
            .let {
                assertEquals(1, it.size)
                assertEquals("Sergey", it[0])
            }

            // Stripping the right default scopes
            scopedUsers.stripDefaultScope()
                .select {
                    exists(scopedUserData.stripDefaultScope().select((scopedUserData.userId eq scopedUsers.id) and (scopedUserData.comment like "%here%"))) or
                    exists(scopedUserData.stripDefaultScope().select((scopedUserData.userId eq scopedUsers.id) and (scopedUserData.comment like "%Sergey")))
                }.orderBy(scopedUsers.id)
                .map { it[scopedUsers.name] }
                .let {
                    assertEquals(2, it.size)
                    assertTrue("Sergey" in it)
                    assertTrue("Something" in it)
                }

        }
    }
}
