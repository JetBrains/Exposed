package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.ddl

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.notLike
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.upperCase
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
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
                .toList()

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
