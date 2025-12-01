package org.jetbrains.exposed.v1.tests.shared.dml

import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Test

class LikeTests : DatabaseTestsBase() {

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
            assertEquals(listOf("_a", "_b"), t.selectAll().where { t.char like LikePattern.ofLiteral("_") + "%" }.map { it[t.char] } as Any)
        }
    }
}
