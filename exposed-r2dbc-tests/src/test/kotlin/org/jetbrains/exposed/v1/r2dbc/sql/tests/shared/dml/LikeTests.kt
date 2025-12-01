package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

class LikeTests : R2dbcDatabaseTestsBase() {

    object t : Table("testTable") {
        val id = integer("charnum")
        val char = varchar("thechar", 255)

        override val primaryKey = PrimaryKey(id)

        init {
            index(false, char)
        }
    }

    @Test
    fun detectSpecialChars() {
        withTables(t) {
            // Lets assume there are no special chars outside this range
            val escapeChar = '+'
            val dialectSpecialChars = db.dialect.likePatternSpecialChars
            val charRange = ('A'..'Z').toSet() + dialectSpecialChars.keys + dialectSpecialChars.values.filterNotNull()

            charRange.forEach { chr ->
                t.insert {
                    it[this.id] = chr.code
                    it[this.char] = "" + chr
                }
            }
            val specialChars = charRange.filter {
                t.selectAll().where { t.char like LikePattern("" + it, escapeChar = escapeChar) }.count() != 1L
            }
            assertEquals(specialChars.toSet(), dialectSpecialChars.keys)
        }
    }

    @Test
    fun testSelectWithLike() {
        withTables(t) {
            var i = 1
            t.insert {
                it[this.id] = i++
                it[this.char] = "%a%"
            }
            t.insert {
                it[this.id] = i++
                it[this.char] = "_a"
            }
            t.insert {
                it[this.id] = i++
                it[this.char] = "_b"
            }
            t.insert {
                it[this.id] = i++
                it[this.char] = "\\a"
            }

            assertEquals("_a", t.selectAll().where { t.char like LikePattern.ofLiteral("_a") }.firstOrNull()?.get(t.char))
            assertEquals("%a%", t.selectAll().where { t.char like LikePattern.ofLiteral("%a%") }.firstOrNull()?.get(t.char))
            assertEquals("\\a", t.selectAll().where { t.char like LikePattern.ofLiteral("\\a") }.firstOrNull()?.get(t.char))

            val result = t.selectAll()
                .where { t.char like LikePattern.ofLiteral("_") + "%" }
                .map { it[t.char] }
                .toList()

            assertContentEquals(listOf("_a", "_b"), result)
        }
    }
}
