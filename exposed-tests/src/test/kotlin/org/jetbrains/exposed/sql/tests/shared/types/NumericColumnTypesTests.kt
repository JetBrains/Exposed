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
                TestDB.SQLITE, TestDB.ORACLE -> "CHECK ($columnName BETWEEN ${Short.MIN_VALUE} and ${Short.MAX_VALUE}))"
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

    @Test
    fun testByteAcceptsOnlyAllowedRange() {
        val testTable = object : Table("test_table") {
            val byte = byte("byte")
        }

        withTables(testTable) { testDb ->
            val columnName = testTable.byte.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                in TestDB.ALL_POSTGRES_LIKE, TestDB.ORACLE, TestDB.SQLITE, TestDB.SQLSERVER ->
                    "CHECK ($columnName BETWEEN ${Byte.MIN_VALUE} and ${Byte.MAX_VALUE}))"
                else -> "($columnName ${testTable.byte.columnType} NOT NULL)"
            }
            assertTrue(testTable.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            testTable.insert { it[byte] = Byte.MIN_VALUE }
            testTable.insert { it[byte] = Byte.MAX_VALUE }
            assertEquals(2, testTable.select(testTable.byte).count())

            val tableName = testTable.nameInDatabaseCase()
            assertFailAndRollback(
                message = "CHECK constraint violation or out-of-range error for MySQL, MariaDB, and H2 (except for H2_V2_PSQL)"
            ) {
                val outOfRangeValue = Byte.MIN_VALUE - 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
            assertFailAndRollback(
                message = "CHECK constraint violation or out-of-range error for MySQL, MariaDB, and H2 (except for H2_V2_PSQL)"
            ) {
                val outOfRangeValue = Byte.MAX_VALUE + 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
        }
    }
}
