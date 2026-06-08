package org.jetbrains.exposed.dao.r2dbc.tests.shared.types

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.v1.core.BinaryColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

class ArrayColumnTypeTests : R2dbcDatabaseTestsBase() {
    private val arrayTypeUnsupportedDb = TestDB.ALL - (TestDB.ALL_POSTGRES + TestDB.H2_V2 + TestDB.H2_V2_PSQL).toSet()

    object ArrayTestTable : IntIdTable("array_test_table") {
        val numbers = array<Int>("numbers").default(listOf(5))
        val strings = array<String?>("strings", TextColumnType()).default(emptyList())
        val doubles = array<Double>("doubles").nullable()
        val byteArray = array("byte_array", BinaryColumnType(32)).nullable()
    }

    class ArrayTestDao(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<ArrayTestDao>(ArrayTestTable)

        var numbers by ArrayTestTable.numbers
        var strings by ArrayTestTable.strings
        var doubles by ArrayTestTable.doubles
    }

    @Test
    fun testArrayColumnWithDAOFunctions() {
        withTestTableAndExcludeSettings {
            val numInput = listOf(1, 2, 3)
            val entity1 = ArrayTestDao.new {
                numbers = numInput
                doubles = null
            }.flush()
            assertContentEquals(numInput, entity1.numbers)
            assertTrue(entity1.strings.isEmpty())

            val doublesInput = listOf(9.0)
            entity1.doubles = doublesInput

            assertContentEquals(doublesInput, ArrayTestDao.all().single().doubles)
        }
    }

    private fun withTestTableAndExcludeSettings(
        vararg tables: Table = arrayOf(ArrayTestTable),
        excludeSettings: Collection<TestDB> = arrayTypeUnsupportedDb,
        statement: suspend R2dbcTransaction.(TestDB) -> Unit
    ) {
        withTables(excludeSettings = excludeSettings, *tables) { db ->
            statement(db)
        }
    }
}
