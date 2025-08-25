package org.jetbrains.exposed.v1.tests.shared.ddl

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.notLike
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.upperCase
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
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
