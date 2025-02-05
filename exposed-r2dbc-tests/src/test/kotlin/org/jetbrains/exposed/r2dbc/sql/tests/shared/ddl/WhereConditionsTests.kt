package org.jetbrains.exposed.r2dbc.sql.tests.shared.ddl

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.upperCase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhereConditionsTests : R2dbcDatabaseTestsBase() {
    object User : Table() {
        val name = varchar("name", 20)
    }

    @Test
    fun whereLikeExpressionTest() {
        withTables(User) {
            User.insert {
                it[name] = "HICHEM"
            }
            val namesResult = User.selectAll().where {
                User.name like stringLiteral("Hich%").upperCase()
            }.map { it[User.name] }
                .toList() // TODO this is the only unmapped Iterable <-> Flow operation; impossible?

            assertEquals(1, namesResult.size)
            assertEquals("HICHEM", namesResult.first())
        }
    }

    @Test
    fun whereNotLikeExpressionTest() {
        withTables(User) {
            User.insert {
                it[name] = "HICHEM"
            }
            val namesResult = User.selectAll().where {
                User.name notLike stringLiteral("Hich%").upperCase()
            }.map { it }
                .toList()

            assertTrue(namesResult.isEmpty())
        }
    }
}
