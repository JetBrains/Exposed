package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.types

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.sql.batchInsert
import org.jetbrains.exposed.v1.r2dbc.sql.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.sql.selectAll
import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEquals
import org.junit.Test

class CharColumnType : R2dbcDatabaseTestsBase() {
    object CharTable : IntIdTable("charTable") {
        val charColumn = char("charColumn")
    }

    @Test
    fun `test char column read and write`() {
        withTables(CharTable) {
            val id = CharTable.insertAndGetId {
                it[charColumn] = 'A'
            }

            val result = CharTable.selectAll().where { CharTable.id eq id }.singleOrNull()

            assertEquals('A', result?.get(CharTable.charColumn))
        }
    }

    @Test
    fun testCharColumnWithCollate() {
        val collateOption = when (TestDB.enabledDialects().first()) {
            TestDB.POSTGRESQL -> "C"
            TestDB.SQLSERVER -> "latin1_general_bin"
            else -> "utf8mb4_bin"
        }

        val tester = object : Table("tester") {
            val letter = char("letter", 1, collate = collateOption)
        }

        // H2 only allows collation for the entire database using SET COLLATION
        // Oracle only allows collation if MAX_STRING_SIZE=EXTENDED, which can only be set in upgrade mode
        // Oracle -> https://docs.oracle.com/en/database/oracle/oracle-database/12.2/refrn/MAX_STRING_SIZE.html#
        withTables(excludeSettings = TestDB.ALL_H2_V2 + TestDB.ORACLE, tester) {
            val letters = listOf("a", "A", "b", "B")
            tester.batchInsert(letters) { ch ->
                this[tester.letter] = ch
            }

            // one of the purposes of collation is to determine ordering rules of stored character data types
            val expected = letters.sortedBy { it.single().code } // [A, B, a, b]
            val actual = tester.selectAll().orderBy(tester.letter).map { it[tester.letter] }
            assertEqualLists(expected, actual)
        }
    }
}
