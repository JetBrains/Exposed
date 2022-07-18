package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.LikePattern
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class LikeTests : DatabaseTestsBase() {

    object t : Table("table") {
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
                t.select { t.char like LikePattern("" + it, escapeChar = escapeChar) }.count() != 1L
            }
            assertEquals(specialChars.toSet(), dialectSpecialChars.keys)
        }
    }

    @Test
    fun testSelectWithLike() {
        withTables(t) {
            var i = 1
            t.insert { it[this.id] = i++; it[this.char] = "%a%" }
            t.insert { it[this.id] = i++; it[this.char] = "_a" }
            t.insert { it[this.id] = i++; it[this.char] = "_b" }
            t.insert { it[this.id] = i++; it[this.char] = "\\a" }

            assertEquals("_a", t.select { t.char like LikePattern.ofLiteral("_a") }.firstOrNull()?.get(t.char))
            assertEquals("%a%", t.select { t.char like LikePattern.ofLiteral("%a%") }.firstOrNull()?.get(t.char))
            assertEquals("\\a", t.select { t.char like LikePattern.ofLiteral("\\a") }.firstOrNull()?.get(t.char))
            assertEquals(listOf("_a", "_b"), t.select { t.char like LikePattern.ofLiteral("_") + "%" }.map { it[t.char] } as Any)
        }
    }

}
