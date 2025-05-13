package org.jetbrains.exposed.v1.tests.shared.types
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.assertEquals
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
            val idFalse = BooleanTable.insertAndGetId {
                it[boolColumn] = booleanParam(false)
            }

            assertEquals(idTrue, BooleanTable.selectAll().where { BooleanTable.boolColumn eq true }.single()[BooleanTable.id])
            assertEquals(idTrue, BooleanTable.selectAll().where { BooleanTable.boolColumn eq booleanParam(true) }.single()[BooleanTable.id])

            assertEquals(idFalse, BooleanTable.selectAll().where { BooleanTable.boolColumn eq false }.single()[BooleanTable.id])
            assertEquals(idFalse, BooleanTable.selectAll().where { BooleanTable.boolColumn eq booleanParam(false) }.single()[BooleanTable.id])
        }
    }

    @Test
    fun testCustomCharBooleanColumnType() {
        val tester = object : Table("tester") {
            val charBooleanColumn = charBoolean("charBooleanColumn")
            val charBooleanColumnWithDefault = charBoolean("charBooleanColumnWithDefault")
                .default(false)
        }

        withTables(tester) {
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
