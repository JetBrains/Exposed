package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class BooleanColumnTypeTests : DatabaseTestsBase() {
    object BooleanTable : IntIdTable("booleanTable") {
        val boolColumn = bool("boolColumn")
    }

    @Test
    fun `true value`() {
        withTables(BooleanTable) {
            val id = BooleanTable.insertAndGetId {
                it[boolColumn] = true
            }

            val result = BooleanTable.selectAll().where { BooleanTable.id eq id }.singleOrNull()
            assertEquals(true, result?.get(BooleanTable.boolColumn))
        }
    }

    @Test
    fun `false value`() {
        withTables(BooleanTable) {
            val id = BooleanTable.insertAndGetId {
                it[boolColumn] = false
            }

            val result = BooleanTable.selectAll().where { BooleanTable.id eq id }.singleOrNull()
            assertEquals(false, result?.get(BooleanTable.boolColumn))
        }
    }

    @Test
    fun `bool in a condition`() {
        withTables(BooleanTable) {
            val idTrue = BooleanTable.insertAndGetId {
                it[boolColumn] = true
            }
            BooleanTable.insertAndGetId {
                it[boolColumn] = false
            }

            val resultTrue = BooleanTable.selectAll().where { BooleanTable.boolColumn eq true }.singleOrNull()
            assertEquals(idTrue, resultTrue?.get(BooleanTable.id))
        }
    }

    @Test
    fun testCustomCharBooleanColumnType() {
        val tester = object : Table("tester") {
            val charBooleanColumn = charBoolean("charBooleanColumn")
            val charBooleanColumnWithDefault = charBoolean("charBooleanColumnWithDefault")
                .default(false)
        }

        withDb {
            try {
                SchemaUtils.create(tester)

                tester.insert {
                    it[charBooleanColumn] = true
                }

                assertEquals(
                    1,
                    tester.select(tester.charBooleanColumn)
                        .where { tester.charBooleanColumn eq true }
                        .andWhere { tester.charBooleanColumnWithDefault eq false }
                        .count()
                )
            } finally {
                SchemaUtils.drop(tester)
            }
        }
    }

    class CharBooleanColumnType(
        private val characterColumnType: VarCharColumnType = VarCharColumnType(1),
    ) : ColumnType<Boolean>() {
        override fun sqlType(): String = characterColumnType.preciseType()

        override fun valueFromDB(value: Any): Boolean =
            when (characterColumnType.valueFromDB(value)) {
                "Y" -> true
                else -> false
            }

        override fun valueToDB(value: Boolean?): Any? =
            characterColumnType.valueToDB(value.toChar().toString())

        override fun nonNullValueToString(value: Boolean): String =
            characterColumnType.nonNullValueToString(value.toChar().toString())

        private fun Boolean?.toChar() = when (this) {
            true -> 'Y'
            false -> 'N'
            else -> ' '
        }
    }

    fun Table.charBoolean(name: String): Column<Boolean> =
        registerColumn(name, CharBooleanColumnType())
}
