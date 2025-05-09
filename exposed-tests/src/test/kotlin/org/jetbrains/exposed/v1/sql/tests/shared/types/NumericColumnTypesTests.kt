package org.jetbrains.exposed.v1.sql.tests.shared.types
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.functions.math.RoundFunction
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.tests.currentDialectTest
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.sql.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.v1.sql.tests.shared.assertTrue
import org.junit.Test
import java.math.BigDecimal

class NumericColumnTypesTests : DatabaseTestsBase() {
    @Test
    fun testShortAcceptsOnlyAllowedRange() {
        val tester = object : Table("tester") {
            val short = short("short_column")
        }

        withTables(tester) { testDb ->
            val columnName = tester.short.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                TestDB.SQLITE, TestDB.ORACLE -> "CHECK ($columnName BETWEEN ${Short.MIN_VALUE} and ${Short.MAX_VALUE}))"
                else -> "($columnName ${tester.short.columnType} NOT NULL)"
            }
            assertTrue(tester.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            tester.insert { it[short] = Short.MIN_VALUE }
            tester.insert { it[short] = Short.MAX_VALUE }
            assertEquals(2, tester.select(tester.short).count())

            val tableName = tester.nameInDatabaseCase()
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
        val tester = object : Table("tester") {
            val byte = byte("byte_column")
        }

        withTables(tester) { testDb ->
            val columnName = tester.byte.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                in TestDB.ALL_POSTGRES_LIKE, TestDB.ORACLE, TestDB.SQLITE, TestDB.SQLSERVER ->
                    "CHECK ($columnName BETWEEN ${Byte.MIN_VALUE} and ${Byte.MAX_VALUE}))"
                else -> "($columnName ${tester.byte.columnType} NOT NULL)"
            }
            assertTrue(tester.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            tester.insert { it[byte] = Byte.MIN_VALUE }
            tester.insert { it[byte] = Byte.MAX_VALUE }
            assertEquals(2, tester.select(tester.byte).count())

            val tableName = tester.nameInDatabaseCase()
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
        val tester = object : Table("tester") {
            val integer = integer("integer_column")
        }

        withTables(tester) { testDb ->
            val columnName = tester.integer.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                TestDB.SQLITE, TestDB.ORACLE -> "CHECK ($columnName BETWEEN ${Int.MIN_VALUE} and ${Int.MAX_VALUE}))"
                else -> "($columnName ${tester.integer.columnType} NOT NULL)"
            }
            assertTrue(tester.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            tester.insert { it[integer] = Int.MIN_VALUE }
            tester.insert { it[integer] = Int.MAX_VALUE }
            assertEquals(2, tester.select(tester.integer).count())

            val tableName = tester.nameInDatabaseCase()
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
    fun testLongAcceptsOnlyAllowedRange() {
        val tester = object : Table("tester") {
            val long = long("long_column")
        }

        withTables(tester) { testDb ->
            val columnName = tester.long.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                TestDB.ORACLE -> "CHECK ($columnName BETWEEN ${Long.MIN_VALUE} and ${Long.MAX_VALUE}))"
                else -> "($columnName ${tester.long.columnType} NOT NULL)"
            }
            assertTrue(tester.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            tester.insert { it[long] = Long.MIN_VALUE }
            tester.insert { it[long] = Long.MAX_VALUE }
            assertEquals(2, tester.select(tester.long).count())

            // SQLite is excluded because it is not possible to enforce the range without a special CHECK constraint
            // that the user can implement if they want to
            if (testDb != TestDB.SQLITE) {
                val tableName = tester.nameInDatabaseCase()
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
    }

    @Test
    fun testUByteAcceptsOnlyAllowedRange() {
        val tester = object : Table("tester") {
            val ubyte = ubyte("ubyte_column")
        }

        withTables(tester) { testDb ->
            val columnName = tester.ubyte.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB, TestDB.SQLSERVER ->
                    "($columnName ${tester.ubyte.columnType} NOT NULL)"
                else -> "CHECK ($columnName BETWEEN ${UByte.MIN_VALUE} and ${UByte.MAX_VALUE}))"
            }
            assertTrue(tester.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            tester.insert { it[ubyte] = UByte.MIN_VALUE }
            tester.insert { it[ubyte] = UByte.MAX_VALUE }
            assertEquals(2, tester.select(tester.ubyte).count())

            val tableName = tester.nameInDatabaseCase()
            assertFailAndRollback(
                message = "CHECK constraint violation (or out-of-range error for MySQL, MariaDB, and SQL Server)"
            ) {
                val outOfRangeValue = UByte.MIN_VALUE.toShort() - 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
            assertFailAndRollback(
                message = "CHECK constraint violation (or out-of-range error for MySQL, MariaDB, and SQL Server)"
            ) {
                val outOfRangeValue = UByte.MAX_VALUE.toShort() + 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
        }
    }

    @Test
    fun testUShortAcceptsOnlyAllowedRange() {
        val tester = object : Table("tester") {
            val ushort = ushort("ushort_column")
        }

        withTables(tester) { testDb ->
            val columnName = tester.ushort.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB -> "($columnName ${tester.ushort.columnType} NOT NULL)"
                else -> "CHECK ($columnName BETWEEN ${UShort.MIN_VALUE} and ${UShort.MAX_VALUE}))"
            }
            assertTrue(tester.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            tester.insert { it[ushort] = UShort.MIN_VALUE }
            tester.insert { it[ushort] = UShort.MAX_VALUE }
            assertEquals(2, tester.select(tester.ushort).count())

            val tableName = tester.nameInDatabaseCase()
            assertFailAndRollback(message = "CHECK constraint violation (or out-of-range error for MySQL and MariaDB)") {
                val outOfRangeValue = UShort.MIN_VALUE.toInt() - 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
            assertFailAndRollback(message = "CHECK constraint violation (or out-of-range error for MySQL and MariaDB)") {
                val outOfRangeValue = UShort.MAX_VALUE.toInt() + 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
        }
    }

    @Test
    fun testUIntegerAcceptsOnlyAllowedRange() {
        val tester = object : Table("tester") {
            val uinteger = uinteger("uinteger_column")
        }

        withTables(tester) { testDb ->
            val columnName = tester.uinteger.nameInDatabaseCase()
            val ddlEnding = when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB -> "($columnName ${tester.uinteger.columnType} NOT NULL)"
                else -> "CHECK ($columnName BETWEEN ${UInt.MIN_VALUE} and ${UInt.MAX_VALUE}))"
            }
            assertTrue(tester.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            tester.insert { it[uinteger] = UInt.MIN_VALUE }
            tester.insert { it[uinteger] = UInt.MAX_VALUE }
            assertEquals(2, tester.select(tester.uinteger).count())

            val tableName = tester.nameInDatabaseCase()
            assertFailAndRollback(message = "CHECK constraint violation (or out-of-range error for MySQL and MariaDB)") {
                val outOfRangeValue = UInt.MIN_VALUE.toLong() - 1
                exec("INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)")
            }
            assertFailAndRollback(message = "CHECK constraint violation (or out-of-range error for MySQL and MariaDB)") {
                val outOfRangeValue = UInt.MAX_VALUE.toLong() + 1
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
            val long = long("long_column", checkConstraintName = "custom_long_check")
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
                    "${tester.long.nameInDatabaseCase()} ${tester.long.columnType} NOT NULL, " +
                    "CONSTRAINT custom_byte_check CHECK (${tester.byte.nameInDatabaseCase()} BETWEEN ${Byte.MIN_VALUE} AND ${Byte.MAX_VALUE}), " +
                    "CONSTRAINT custom_ubyte_check CHECK (${tester.ubyte.nameInDatabaseCase()} BETWEEN 0 AND ${UByte.MAX_VALUE}), " +
                    "CONSTRAINT custom_short_check CHECK (${tester.short.nameInDatabaseCase()} BETWEEN ${Short.MIN_VALUE} AND ${Short.MAX_VALUE}), " +
                    "CONSTRAINT custom_ushort_check CHECK (${tester.ushort.nameInDatabaseCase()} BETWEEN 0 AND ${UShort.MAX_VALUE}), " +
                    "CONSTRAINT custom_integer_check CHECK (${tester.integer.nameInDatabaseCase()} BETWEEN ${Int.MIN_VALUE} AND ${Int.MAX_VALUE}), " +
                    "CONSTRAINT custom_uinteger_check CHECK (${tester.uinteger.nameInDatabaseCase()} BETWEEN 0 AND ${UInt.MAX_VALUE}), " +
                    "CONSTRAINT custom_long_check CHECK (${tester.long.nameInDatabaseCase()} BETWEEN ${Long.MIN_VALUE} AND ${Long.MAX_VALUE}))",
                tester.ddl
            )
        }
    }
}
