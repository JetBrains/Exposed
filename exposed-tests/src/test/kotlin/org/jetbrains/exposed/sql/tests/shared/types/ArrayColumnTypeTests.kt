package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.junit.Test

class ArrayColumnTypeTests : DatabaseTestsBase() {
    private val arrayTypeUnsupportedDb = TestDB.entries - (TestDB.postgreSQLRelatedDB + TestDB.H2 + TestDB.H2_PSQL).toSet()

    object ArrayTestTable : IntIdTable("array_test_table") {
        val numbers = array<Int>("numbers", IntegerColumnType()).default(listOf(5))
        val strings = array<String?>("strings", TextColumnType()).default(emptyList())
        val doubles = array<Double>("doubles", DoubleColumnType()).nullable()
    }

    @Test
    fun testCreateAndDropArrayColumns() {
        withDb(excludeSettings = arrayTypeUnsupportedDb) {
            try {
                SchemaUtils.create(ArrayTestTable)
                assertTrue(ArrayTestTable.exists())
            } finally {
                SchemaUtils.drop(ArrayTestTable)
            }
        }
    }

    @Test
    fun testCreateMissingColumnsWithDefaults() {
        withTables(excludeSettings = arrayTypeUnsupportedDb, ArrayTestTable) {
            try {
                SchemaUtils.createMissingTablesAndColumns(ArrayTestTable)
                assertTrue(SchemaUtils.statementsRequiredToActualizeScheme(ArrayTestTable).isEmpty())
            } finally {
                SchemaUtils.drop(ArrayTestTable)
            }
        }
    }
}
