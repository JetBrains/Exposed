package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.junit.Test

class CreateIndexTests : DatabaseTestsBase() {

    @Test
    fun createStandardIndex() {
        val TestTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
        }

        withTables(excludeSettings = listOf(TestDB.H2_MYSQL), tables = *arrayOf(TestTable)) {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertTrue(TestTable.exists())
            SchemaUtils.drop(TestTable)
        }
    }

    @Test
    fun createHashIndex() {
        val TestTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byNameHash = index("test_table_by_name", /* isUnique = */ false, name, indexType = "HASH")
        }

        withTables(excludeSettings = listOf(TestDB.H2_MYSQL, TestDB.SQLSERVER), tables = *arrayOf(TestTable)) {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertTrue(TestTable.exists())
            SchemaUtils.drop(TestTable)
        }
    }

    @Test
    fun createNonClusteredSQLServerIndex() {
        val TestTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byNameHash = index("test_table_by_name", /* isUnique = */ false, name, indexType = "NONCLUSTERED")
        }

        withDb(TestDB.SQLSERVER) {
            SchemaUtils.createMissingTablesAndColumns(TestTable)
            assertTrue(TestTable.exists())
            SchemaUtils.drop(TestTable)
        }
    }
}
