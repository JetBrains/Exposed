package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.functions.math.RoundFunction
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.junit.Test
import java.math.BigDecimal

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

    @Test
    fun testIntegerAcceptsOnlyAllowedRange() {
        val testTable = object : Table("test_table") {
            val integer = integer("integer_column")
        }

        withTables(testTable) { testDb ->
            val columnName = testTable.integer.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                TestDB.SQLITE, TestDB.ORACLE -> "CHECK ($columnName BETWEEN ${Int.MIN_VALUE} and ${Int.MAX_VALUE}))"
                else -> "($columnName ${testTable.integer.columnType} NOT NULL)"
            }
            assertTrue(testTable.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            testTable.insert { it[integer] = Int.MIN_VALUE }
            testTable.insert { it[integer] = Int.MAX_VALUE }
            assertEquals(2, testTable.select(testTable.integer).count())

            val tableName = testTable.nameInDatabaseCase()
            assertFailAndRollback(message = "Out-of-range error (or CHECK constraint violation for SQLite & Oracle)") {
                val outOfRangeValue = Int.MIN_VALUE.toLong() - 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
            assertFailAndRollback(message = "Out-of-range error (or CHECK constraint violation for SQLite & Oracle)") {
                val outOfRangeValue = Int.MAX_VALUE.toLong() + 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
        }
    }

    @Test
    fun testParams() {
        val testTable = object : Table("test_table") {
            val byte = byte("byte_column")
            val ubyte = ubyte("ubyte_column")
            val short = short("short_column")
            val ushort = ushort("ushort_column")
            val integer = integer("integer_column")
            val uinteger = uinteger("uinteger_column")
            val long = long("long_column")
            val ulong = ulong("ulong_column")
            val float = float("float_column")
            val double = double("double_column")
            val decimal = decimal("decimal_column", 6, 3)
        }

        withTables(testTable) {
            testTable.insert {
                it[byte] = byteParam(Byte.MIN_VALUE)
                it[ubyte] = ubyteParam(UByte.MAX_VALUE)
                it[short] = shortParam(Short.MIN_VALUE)
                it[ushort] = ushortParam(UShort.MAX_VALUE)
                it[integer] = intParam(Int.MIN_VALUE)
                it[uinteger] = uintParam(UInt.MAX_VALUE)
                it[long] = longParam(Long.MIN_VALUE)
                it[ulong] = ulongParam(Long.MAX_VALUE.toULong())
                it[float] = floatParam(3.14159F)
                it[double] = doubleParam(3.1415926535)
                it[decimal] = decimalParam(123.456.toBigDecimal())
            }

            assertEquals(1, testTable.selectAll().where { testTable.byte eq byteParam(Byte.MIN_VALUE) }.count())
            assertEquals(1, testTable.selectAll().where { testTable.ubyte eq ubyteParam(UByte.MAX_VALUE) }.count())
            assertEquals(1, testTable.selectAll().where { testTable.short eq shortParam(Short.MIN_VALUE) }.count())
            assertEquals(1, testTable.selectAll().where { testTable.ushort eq ushortParam(UShort.MAX_VALUE) }.count())
            assertEquals(1, testTable.selectAll().where { testTable.integer eq intParam(Int.MIN_VALUE) }.count())
            assertEquals(1, testTable.selectAll().where { testTable.uinteger eq uintParam(UInt.MAX_VALUE) }.count())
            assertEquals(1, testTable.selectAll().where { testTable.long eq longParam(Long.MIN_VALUE) }.count())
            assertEquals(1, testTable.selectAll().where { testTable.ulong eq ulongParam(Long.MAX_VALUE.toULong()) }.count())
            assertEquals(1, testTable.selectAll().where { testTable.double eq doubleParam(3.1415926535) }.count())
            assertEquals(1, testTable.selectAll().where { testTable.decimal eq decimalParam(123.456.toBigDecimal()) }.count())
            assertEquals(
                1,
                testTable.selectAll().where {
                    if (currentDialectTest is MysqlDialect) {
                        RoundFunction(testTable.float, 5).eq<Number, BigDecimal, Float>(floatParam(3.14159F))
                    } else {
                        testTable.float eq floatParam(3.14159F)
                    }
                }.count()
            )
        }
    }
}
