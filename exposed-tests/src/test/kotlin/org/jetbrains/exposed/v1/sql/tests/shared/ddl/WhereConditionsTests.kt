package org.jetbrains.exposed.v1.sql.tests.shared.ddl

import org.jetbrains.exposed.v1.sql.Table
import org.jetbrains.exposed.v1.sql.insert
import org.jetbrains.exposed.v1.sql.selectAll
import org.jetbrains.exposed.v1.sql.stringLiteral
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.upperCase
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
