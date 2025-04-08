package org.jetbrains.exposed.r2dbc.sql.tests.shared.types

import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.r2dbc.sql.tests.currentDialectTest
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertEquals
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.functions.math.RoundFunction
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.junit.Test
import java.math.BigDecimal

class NumericColumnTypesTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testShortAcceptsOnlyAllowedRange() {
        val testTable = object : Table("test_table") {
            val short = short("short")
        }

        withTables(testTable) { testDb ->
            val columnName = testTable.short.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                TestDB.ORACLE -> "CHECK ($columnName BETWEEN ${Short.MIN_VALUE} and ${Short.MAX_VALUE}))"
                else -> "($columnName ${testTable.short.columnType} NOT NULL)"
            }
            assertTrue(testTable.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            testTable.insert { it[short] = Short.MIN_VALUE }
            testTable.insert { it[short] = Short.MAX_VALUE }
            assertEquals(2, testTable.select(testTable.short).count())

            val tableName = testTable.nameInDatabaseCase()
            assertFailAndRollback(message = "Out-of-range error (or CHECK constraint violation for Oracle)") {
                val outOfRangeValue = Short.MIN_VALUE - 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
            assertFailAndRollback(message = "Out-of-range error (or CHECK constraint violation for Oracle)") {
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
                in TestDB.ALL_POSTGRES_LIKE, TestDB.ORACLE, TestDB.SQLSERVER ->
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
                TestDB.ORACLE -> "CHECK ($columnName BETWEEN ${Int.MIN_VALUE} and ${Int.MAX_VALUE}))"
                else -> "($columnName ${testTable.integer.columnType} NOT NULL)"
            }
            assertTrue(testTable.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            testTable.insert { it[integer] = Int.MIN_VALUE }
            testTable.insert { it[integer] = Int.MAX_VALUE }
            assertEquals(2, testTable.select(testTable.integer).count())

            val tableName = testTable.nameInDatabaseCase()
            assertFailAndRollback(message = "Out-of-range error (or CHECK constraint violation for Oracle)") {
                val outOfRangeValue = Int.MIN_VALUE.toLong() - 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
            assertFailAndRollback(message = "Out-of-range error (or CHECK constraint violation for Oracle)") {
                val outOfRangeValue = Int.MAX_VALUE.toLong() + 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
        }
    }

    @Test
    fun testLongAcceptsOnlyAllowedRange() {
        val testTable = object : Table("test_table") {
            val long = long("long_column")
        }

        withTables(testTable) { testDb ->
            val columnName = testTable.long.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                TestDB.ORACLE -> "CHECK ($columnName BETWEEN ${Long.MIN_VALUE} and ${Long.MAX_VALUE}))"
                else -> "($columnName ${testTable.long.columnType} NOT NULL)"
            }
            assertTrue(testTable.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            testTable.insert { it[long] = Long.MIN_VALUE }
            testTable.insert { it[long] = Long.MAX_VALUE }
            assertEquals(2, testTable.select(testTable.long).count())

            val tableName = testTable.nameInDatabaseCase()
            assertFailAndRollback(message = "Out-of-range error (or CHECK constraint violation for Oracle)") {
                val outOfRangeValue = Long.MIN_VALUE.toBigDecimal() - 1.toBigDecimal()
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
            assertFailAndRollback(message = "Out-of-range error (or CHECK constraint violation for Oracle)") {
                val outOfRangeValue = Long.MAX_VALUE.toBigDecimal() + 1.toBigDecimal()
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

    @Test
    fun testCustomCheckConstraintName() {
        val tester = object : Table("tester") {
            val byte = byte("byte_column", checkConstraintName = "custom_byte_check")
            val ubyte = ubyte("ubyte_column", checkConstraintName = "custom_ubyte_check")
            val short = short("short_column", checkConstraintName = "custom_short_check")
            val ushort = ushort("ushort_column", checkConstraintName = "custom_ushort_check")
            val integer = integer("integer_column", checkConstraintName = "custom_integer_check")
            val uinteger = uinteger("uinteger_column", checkConstraintName = "custom_uinteger_check")
        }

        withTables(tester) {
            assertEquals(
                "CREATE TABLE ${addIfNotExistsIfSupported()}${tester.nameInDatabaseCase()} (" +
                    "${tester.byte.nameInDatabaseCase()} ${tester.byte.columnType} NOT NULL, " +
                    "${tester.ubyte.nameInDatabaseCase()} ${tester.ubyte.columnType} NOT NULL, " +
                    "${tester.short.nameInDatabaseCase()} ${tester.short.columnType} NOT NULL, " +
                    "${tester.ushort.nameInDatabaseCase()} ${tester.ushort.columnType} NOT NULL, " +
                    "${tester.integer.nameInDatabaseCase()} ${tester.integer.columnType} NOT NULL, " +
                    "${tester.uinteger.nameInDatabaseCase()} ${tester.uinteger.columnType} NOT NULL, " +
                    "CONSTRAINT custom_byte_check CHECK (${tester.byte.nameInDatabaseCase()} BETWEEN ${Byte.MIN_VALUE} AND ${Byte.MAX_VALUE}), " +
                    "CONSTRAINT custom_ubyte_check CHECK (${tester.ubyte.nameInDatabaseCase()} BETWEEN 0 AND ${UByte.MAX_VALUE}), " +
                    "CONSTRAINT custom_short_check CHECK (${tester.short.nameInDatabaseCase()} BETWEEN ${Short.MIN_VALUE} AND ${Short.MAX_VALUE}), " +
                    "CONSTRAINT custom_ushort_check CHECK (${tester.ushort.nameInDatabaseCase()} BETWEEN 0 AND ${UShort.MAX_VALUE}), " +
                    "CONSTRAINT custom_integer_check CHECK (${tester.integer.nameInDatabaseCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE}), " +
                    "CONSTRAINT custom_uinteger_check CHECK (${tester.uinteger.nameInDatabaseCase()} BETWEEN 0 AND ${UInt.MAX_VALUE}))",
                tester.ddl
            )
        }
    }
}
