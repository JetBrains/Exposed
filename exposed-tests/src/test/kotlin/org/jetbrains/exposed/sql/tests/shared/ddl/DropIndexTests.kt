package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test

class DropIndexTests : DatabaseTestsBase() {

    @Test
    fun dropStandardIndex() {
        val testTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
        }

        withTables(tables = arrayOf(testTable)) {
            val creationStatements = MigrationUtils.statementsRequiredForDatabaseMigration(testTable)
            creationStatements.forEach { exec(it) }
            assertTrue(testTable.exists())
            val indicesBeforeDrop = getIndices(testTable)
            val byNameIndex = testTable.indices.single()
            assertEquals(1, indicesBeforeDrop.filter { it.indexName == byNameIndex.indexName || it.onlyNameDiffer(byNameIndex) }.size)
            val dropStatement = byNameIndex.dropStatement().single()
            exec(dropStatement)
            val indicesAfterDrop = getIndices(testTable)
            assertEquals(0, indicesAfterDrop.filter { it.indexName == byNameIndex.indexName || it.onlyNameDiffer(byNameIndex) }.size)
            SchemaUtils.drop(testTable)
        }
    }

    @Test
    fun dropHashIndex() {
        val testTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byNameHash = index("test_table_by_name", isUnique = false, name, indexType = "HASH")
        }

        withTables(
            excludeSettings = TestDB.ALL_H2 + listOf(TestDB.SQLSERVER, TestDB.ORACLE, TestDB.SQLITE),
            tables = arrayOf(testTable)
        ) {
            val creationStatements = MigrationUtils.statementsRequiredForDatabaseMigration(testTable)
            creationStatements.forEach { exec(it) }
            assertTrue(testTable.exists())
            val byNameHash = testTable.indices.single()
            val indicesBeforeDrop = getIndices(testTable)
            assertEquals(1, indicesBeforeDrop.filter { it.indexName == byNameHash.indexName || it.onlyNameDiffer(byNameHash) }.size)
            val dropStatement = byNameHash.dropStatement().single()
            exec(dropStatement)
            val indicesAfterDrop = getIndices(testTable)
            assertEquals(0, indicesAfterDrop.filter { it.indexName == byNameHash.indexName || it.onlyNameDiffer(byNameHash) }.size)
            SchemaUtils.drop(testTable)
        }
    }

    @Test
    fun dropUniqueIndex() {
        val testTable = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", true, name)
        }

        withTables(tables = arrayOf(testTable)) {
            val creationStatements = MigrationUtils.statementsRequiredForDatabaseMigration(testTable)
            creationStatements.forEach { exec(it) }
            assertTrue(testTable.exists())
            val byNameIndex = testTable.indices.single()
            val indicesBeforeDrop = getIndices(testTable)
            assertEquals(1, indicesBeforeDrop.filter { it.indexName == byNameIndex.indexName || it.onlyNameDiffer(byNameIndex) }.size)
            val dropStatement = byNameIndex.dropStatement().single()
            exec(dropStatement)
            val indicesAfterDrop = getIndices(testTable)
            assertEquals(0, indicesAfterDrop.filter { it.indexName == byNameIndex.indexName || it.onlyNameDiffer(byNameIndex) }.size)
            SchemaUtils.drop(testTable)
        }
    }

    @Test
    fun dropFunctionalIndex() {
        val testTable = object : IntIdTable("test_table") {
            val price = integer("price")
            val item = varchar("item", 32).nullable()

            init {
                uniqueIndex(columns = arrayOf(price), functions = listOf(Coalesce(item, stringLiteral("*"))))
            }
        }

        val functionsNotSupported = TestDB.ALL_MARIADB + TestDB.ALL_H2 + TestDB.SQLSERVER + TestDB.MYSQL_V5
        // Note that SQLITE is excluded because the index names of the defined table and the actual indices are different
        withTables(excludeSettings = functionsNotSupported + listOf(TestDB.SQLITE), testTable) {
            val creationStatements = MigrationUtils.statementsRequiredForDatabaseMigration(testTable)
            creationStatements.forEach { exec(it) }
            assertTrue(testTable.exists())
            val functionalIndex = testTable.indices.single()
            val indicesBeforeDrop = getIndices(testTable)
            assertEquals(1, indicesBeforeDrop.filter { it.indexName == functionalIndex.indexName || it.onlyNameDiffer(functionalIndex) }.size)
            val dropStatement = functionalIndex.dropStatement().single()
            exec(dropStatement)
            val indicesAfterDrop = getIndices(testTable)
            assertEquals(0, indicesAfterDrop.filter { it.indexName == functionalIndex.indexName || it.onlyNameDiffer(functionalIndex) }.size)
            SchemaUtils.drop(testTable)
        }
    }

    private fun Transaction.getIndices(table: Table): List<Index> {
        db.dialect.resetCaches()
        return currentDialect.existingIndices(table)[table].orEmpty()
    }
}
