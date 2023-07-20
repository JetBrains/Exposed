package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class UnsignedColumnTypeTests : DatabaseTestsBase() {
    object UByteTable : Table("uByteTable") {
        val unsignedByte = ubyte("uByte")
    }

    object UShortTable : Table("uShortTable") {
        val unsignedShort = ushort("uShort")
    }

    object UIntTable : Table("uIntTable") {
        val unsignedInt = uinteger("uInt")
    }

    object ULongTable : Table("uLongTable") {
        val unsignedLong = ulong("uLong")
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
