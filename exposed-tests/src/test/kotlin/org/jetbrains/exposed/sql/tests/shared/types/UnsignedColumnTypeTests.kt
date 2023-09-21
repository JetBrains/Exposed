package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.junit.Test

class UnsignedColumnTypeTests : DatabaseTestsBase() {
    object UByteTable : Table("ubyte_table") {
        val unsignedByte = ubyte("ubyte")
    }

    object UShortTable : Table("ushort_table") {
        val unsignedShort = ushort("ushort")
    }

    object UIntTable : Table("uint_table") {
        val unsignedInt = uinteger("uint")
    }

    object ULongTable : Table("ulong_table") {
        val unsignedLong = ulong("ulong")
    }

    @Test
    fun testUByteColumnType() {
        withTables(UByteTable) {
            UByteTable.insert {
                it[unsignedByte] = 123u
            }

            val result = UByteTable.selectAll().toList()
            assertEquals(1, result.size)
            assertEquals(123u, result.single()[UByteTable.unsignedByte])
        }
    }

    @Test
    fun testUByteWithCheckConstraint() {
        withTables(UByteTable) {
            val ddlEnding = when (currentDialectTest) {
                is MysqlDialect -> "(ubyte TINYINT UNSIGNED NOT NULL)"
                is SQLServerDialect -> "(ubyte TINYINT NOT NULL)"
                else -> "CHECK (ubyte BETWEEN 0 and ${UByte.MAX_VALUE}))"
            }
            assertTrue(UByteTable.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            val number = 191.toUByte()
            assertTrue(number in Byte.MAX_VALUE.toUByte()..UByte.MAX_VALUE)

            UByteTable.insert { it[unsignedByte] = number }

            val result = UByteTable.selectAll()
            assertEquals(number, result.single()[UByteTable.unsignedByte])

            // test that column itself blocks same out-of-range value that compiler blocks
            assertFailAndRollback("Check constraint violation (or out-of-range error in MySQL/MariaDB/SQL Server)") {
                val tableName = UByteTable.nameInDatabaseCase()
                val columnName = UByteTable.unsignedByte.nameInDatabaseCase()
                val outOfRangeValue = UByte.MAX_VALUE + 1u
                exec("""INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)""")
            }
        }
    }

    @Test
    fun testPreviousUByteColumnTypeWorksWithNewSmallIntType() {
        // MySQL and MariaDB type hasn't changed, and PostgreSQL and Oracle never supported TINYINT
        withDb(TestDB.allH2TestDB - TestDB.H2_PSQL + TestDB.SQLITE) { testDb ->
            try {
                val tableName = UByteTable.nameInDatabaseCase()
                val columnName = UByteTable.unsignedByte.nameInDatabaseCase()
                // create table using previous column type TINYINT
                exec("""CREATE TABLE ${addIfNotExistsIfSupported()}$tableName ($columnName TINYINT NOT NULL)""")

                val number1 = Byte.MAX_VALUE.toUByte()
                UByteTable.insert { it[unsignedByte] = number1 }

                val result1 = UByteTable.select { UByteTable.unsignedByte eq number1 }.count()
                assertEquals(1, result1)

                // TINYINT maps to INTEGER in SQLite, so it will not throw OoR error
                if (testDb != TestDB.SQLITE) {
                    val number2 = (Byte.MAX_VALUE + 1).toUByte()
                    assertFailAndRollback("Out-of-range (OoR) error") {
                        UByteTable.insert { it[unsignedByte] = number2 }
                        assertEquals(0, UByteTable.select { UByteTable.unsignedByte less 0u }.count())
                    }

                    // modify column to now have SMALLINT type
                    exec(UByteTable.unsignedByte.modifyStatement().first())
                    UByteTable.insert { it[unsignedByte] = number2 }

                    val result2 = UByteTable.selectAll().map { it[UByteTable.unsignedByte] }
                    assertEqualCollections(listOf(number1, number2), result2)
                }
            } finally {
                SchemaUtils.drop(UByteTable)
            }
        }
    }

    @Test
    fun testUShortColumnType() {
        withTables(UShortTable) {
            UShortTable.insert {
                it[unsignedShort] = 123u
            }

            val result = UShortTable.selectAll().toList()
            assertEquals(1, result.size)
            assertEquals(123u, result.single()[UShortTable.unsignedShort])
        }
    }

    @Test
    fun testUShortWithCheckConstraint() {
        withTables(UShortTable) {
            val ddlEnding = if (currentDialectTest is MysqlDialect) {
                "(ushort SMALLINT UNSIGNED NOT NULL)"
            } else {
                "CHECK (ushort BETWEEN 0 and ${UShort.MAX_VALUE}))"
            }
            assertTrue(UShortTable.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            val number = 49151.toUShort()
            assertTrue(number in Short.MAX_VALUE.toUShort()..UShort.MAX_VALUE)

            UShortTable.insert { it[unsignedShort] = number }

            val result = UShortTable.selectAll()
            assertEquals(number, result.single()[UShortTable.unsignedShort])

            // test that column itself blocks same out-of-range value that compiler blocks
            assertFailAndRollback("Check constraint violation (or out-of-range error in MySQL/MariaDB)") {
                val tableName = UShortTable.nameInDatabaseCase()
                val columnName = UShortTable.unsignedShort.nameInDatabaseCase()
                val outOfRangeValue = UShort.MAX_VALUE + 1u
                exec("""INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)""")
            }
        }
    }

    @Test
    fun testPreviousUShortColumnTypeWorksWithNewIntType() {
        withDb(excludeSettings = listOf(TestDB.MYSQL, TestDB.MARIADB)) { testDb ->
            try {
                val tableName = UShortTable.nameInDatabaseCase()
                val columnName = UShortTable.unsignedShort.nameInDatabaseCase()
                // create table using previous column type SMALLINT
                exec("""CREATE TABLE ${addIfNotExistsIfSupported()}$tableName ($columnName SMALLINT NOT NULL)""")

                val number1 = Short.MAX_VALUE.toUShort()
                UShortTable.insert { it[unsignedShort] = number1 }

                val result1 = UShortTable.select { UShortTable.unsignedShort eq number1 }.count()
                assertEquals(1, result1)

                // SMALLINT maps to INTEGER in SQLite and NUMBER(38) in Oracle, so they will not throw OoR error
                if (testDb != TestDB.SQLITE && testDb != TestDB.ORACLE) {
                    val number2 = (Short.MAX_VALUE + 1).toUShort()
                    assertFailAndRollback("Out-of-range (OoR) error") {
                        UShortTable.insert { it[unsignedShort] = number2 }
                        assertEquals(0, UShortTable.select { UShortTable.unsignedShort less 0u }.count())
                    }

                    // modify column to now have INT type
                    exec(UShortTable.unsignedShort.modifyStatement().first())
                    UShortTable.insert { it[unsignedShort] = number2 }

                    val result2 = UShortTable.selectAll().map { it[UShortTable.unsignedShort] }
                    assertEqualCollections(listOf(number1, number2), result2)
                }
            } finally {
                SchemaUtils.drop(UShortTable)
            }
        }
    }

    @Test
    fun testUIntColumnType() {
        withTables(UIntTable) {
            UIntTable.insert {
                it[unsignedInt] = 123u
            }

            val result = UIntTable.selectAll().toList()
            assertEquals(1, result.size)
            assertEquals(123u, result.single()[UIntTable.unsignedInt])
        }
    }

    @Test
    fun testUIntWithCheckConstraint() {
        withTables(UIntTable) {
            val ddlEnding = if (currentDialectTest is MysqlDialect) {
                "(uint INT UNSIGNED NOT NULL)"
            } else {
                "CHECK (uint BETWEEN 0 and ${UInt.MAX_VALUE}))"
            }
            assertTrue(UIntTable.ddl.single().endsWith(ddlEnding, ignoreCase = true))

            val number = 3_221_225_471u
            assertTrue(number in Int.MAX_VALUE.toUInt()..UInt.MAX_VALUE)

            UIntTable.insert { it[unsignedInt] = number }

            val result = UIntTable.selectAll()
            assertEquals(number, result.single()[UIntTable.unsignedInt])

            // test that column itself blocks same out-of-range value that compiler blocks
            assertFailAndRollback("Check constraint violation (or out-of-range error in MySQL/MariaDB)") {
                val tableName = UIntTable.nameInDatabaseCase()
                val columnName = UIntTable.unsignedInt.nameInDatabaseCase()
                val outOfRangeValue = UInt.MAX_VALUE.toLong() + 1L
                exec("""INSERT INTO $tableName ($columnName) VALUES ($outOfRangeValue)""")
            }
        }
    }

    @Test
    fun testPreviousUIntColumnTypeWorksWithNewBigIntType() {
        // Oracle was already previously constrained to NUMBER(13)
        withDb(excludeSettings = listOf(TestDB.MYSQL, TestDB.MARIADB, TestDB.ORACLE)) { testDb ->
            try {
                val tableName = UIntTable.nameInDatabaseCase()
                val columnName = UIntTable.unsignedInt.nameInDatabaseCase()
                // create table using previous column type INT
                exec("""CREATE TABLE ${addIfNotExistsIfSupported()}$tableName ($columnName INT NOT NULL)""")

                val number1 = Int.MAX_VALUE.toUInt()
                UIntTable.insert { it[unsignedInt] = number1 }

                val result1 = UIntTable.select { UIntTable.unsignedInt eq number1 }.count()
                assertEquals(1, result1)

                // INT maps to INTEGER in SQLite, so it will not throw OoR error
                if (testDb != TestDB.SQLITE) {
                    val number2 = Int.MAX_VALUE.toUInt() + 1u
                    assertFailAndRollback("Out-of-range (OoR) error") {
                        UIntTable.insert { it[unsignedInt] = number2 }
                        assertEquals(0, UIntTable.select { UIntTable.unsignedInt less 0u }.count())
                    }

                    // modify column to now have BIGINT type
                    exec(UIntTable.unsignedInt.modifyStatement().first())
                    UIntTable.insert { it[unsignedInt] = number2 }

                    val result2 = UIntTable.selectAll().map { it[UIntTable.unsignedInt] }
                    assertEqualCollections(listOf(number1, number2), result2)
                }
            } finally {
                SchemaUtils.drop(UIntTable)
            }
        }
    }

    @Test
    fun testULongColumnType() {
        withTables(ULongTable) {
            ULongTable.insert {
                it[unsignedLong] = 123uL
            }

            val result = ULongTable.selectAll().toList()
            assertEquals(1, result.size)
            assertEquals(123uL, result.single()[ULongTable.unsignedLong])
        }
    }

    @Test
    fun testMaxUnsignedTypesInMySql() {
        withDb(listOf(TestDB.MYSQL, TestDB.MARIADB)) {
            SchemaUtils.create(UByteTable, UShortTable, UIntTable, ULongTable)

            UByteTable.insert { it[unsignedByte] = UByte.MAX_VALUE }
            assertEquals(UByte.MAX_VALUE, UByteTable.selectAll().single()[UByteTable.unsignedByte])

            UShortTable.insert { it[unsignedShort] = UShort.MAX_VALUE }
            assertEquals(UShort.MAX_VALUE, UShortTable.selectAll().single()[UShortTable.unsignedShort])

            UIntTable.insert { it[unsignedInt] = UInt.MAX_VALUE }
            assertEquals(UInt.MAX_VALUE, UIntTable.selectAll().single()[UIntTable.unsignedInt])

            ULongTable.insert { it[unsignedLong] = ULong.MAX_VALUE }
            assertEquals(ULong.MAX_VALUE, ULongTable.selectAll().single()[ULongTable.unsignedLong])

            SchemaUtils.drop(UByteTable, UShortTable, UIntTable, ULongTable)
        }
    }

    @Test
    fun testCheckConstraintNameAcrossMultipleTables() {
        val (col1, col2, col3) = listOf("num1", "num2", "num3")
        val tester1 = object : Table("tester_1") {
            val unsigned1 = ubyte(col1)
            val unsigned2 = ushort(col2)
            val unsigned3 = uinteger(col3)
        }
        val tester2 = object : Table("tester_2") {
            val unsigned1 = ubyte(col1)
            val unsigned2 = ushort(col2)
            val unsigned3 = uinteger(col3)
        }

        withDb {
            try {
                SchemaUtils.create(tester1, tester2)

                val (byte, short, integer) = Triple(191.toUByte(), 49151.toUShort(), 3_221_225_471u)
                tester1.insert {
                    it[unsigned1] = byte
                    it[unsigned2] = short
                    it[unsigned3] = integer
                }
                tester2.insert {
                    it[unsigned1] = byte
                    it[unsigned2] = short
                    it[unsigned3] = integer
                }
            } finally {
                SchemaUtils.drop(tester1, tester2)
            }
        }
    }
}
