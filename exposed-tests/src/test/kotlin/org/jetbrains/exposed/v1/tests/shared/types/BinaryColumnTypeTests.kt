package org.jetbrains.exposed.v1.tests.shared.types

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BinaryColumnTypeTests : DatabaseTestsBase() {

    private object BinaryTestTable : Table("binary_cast_simple") {
        val id = integer("id")
        val data = binary("data", 4)

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testLongerValueDoesNotMatchStoredValue() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_H2_V2, BinaryTestTable) {
            addLogger(StdOutSqlLogger)
            BinaryTestTable.insert {
                it[id] = 1
                it[data] = byteArrayOf(1, 2, 3, 4)
            }

            // A different value that merely starts with the same four bytes.
            val longerValue = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

            val matches = BinaryTestTable.selectAll().where { BinaryTestTable.data eq longerValue }.count()

            assertEquals(
                0L,
                matches,
                "an 8-byte value matched the stored 4-byte row - the comparison argument was " +
                    "truncated to its first 4 bytes by cast(? as VARBINARY(4))"
            )
        }
    }
}
