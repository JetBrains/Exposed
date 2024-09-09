package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.junit.Test

class NumericColumnTypesTests : DatabaseTestsBase() {
    @Test
    fun testShortAcceptsOnlyAllowedRange() {
        val testTable = object : Table("test_table") {
            val short = short("short")
        }

        withTables(testTable) { testDb ->
            val columnName = testTable.short.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                TestDB.SQLITE, in TestDB.ALL_ORACLE_LIKE -> "CHECK ($columnName BETWEEN ${Short.MIN_VALUE} and ${Short.MAX_VALUE}))"
                else -> "($columnName ${testTable.short.columnType} NOT NULL)"
            }
            assertTrue(testTable.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            testTable.insert { it[short] = Short.MIN_VALUE }
            testTable.insert { it[short] = Short.MAX_VALUE }
            assertEquals(2, testTable.select(testTable.short).count())

            val tableName = testTable.nameInDatabaseCase()
            assertFailAndRollback(message = "Out-of-range error (or CHECK constraint violation for SQLite & Oracle)") {
                val outOfRangeValue = Short.MIN_VALUE - 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
            assertFailAndRollback(message = "Out-of-range error (or CHECK constraint violation for SQLite & Oracle)") {
                val outOfRangeValue = Short.MAX_VALUE + 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
        }
    }
}
