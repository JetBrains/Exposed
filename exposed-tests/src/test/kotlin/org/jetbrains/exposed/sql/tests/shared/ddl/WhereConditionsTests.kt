package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.upperCase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This class contains tests for logical sql operators
 */
class WhereConditionsTests : DatabaseTestsBase() {
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

            assertTrue(namesResult.isEmpty())
        }
    }
}
