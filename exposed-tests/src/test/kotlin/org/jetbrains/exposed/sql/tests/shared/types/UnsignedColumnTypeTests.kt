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
    fun testUShortTypeRegression() {
        withDb(excludeSettings = listOf(TestDB.MYSQL, TestDB.MARIADB)) { testDb ->
            try {
                val tableName = UShortTable.nameInDatabaseCase()
                val columnName = UShortTable.unsignedShort.nameInDatabaseCase()
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
                        assertEquals(0, UShortTable.select { UShortTable.unsignedShort less 0u })
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
}
