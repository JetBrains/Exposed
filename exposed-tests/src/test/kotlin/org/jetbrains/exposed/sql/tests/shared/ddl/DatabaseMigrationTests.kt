package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.PrimaryKeyMetadata
import org.junit.Test
import java.io.File
import kotlin.properties.Delegates
import kotlin.test.assertNull

@OptIn(ExperimentalDatabaseMigrationApi::class)
class DatabaseMigrationTests : DatabaseTestsBase() {

    @Test
    fun testMigrationScriptDirectoryAndContent() {
        val tableName = "tester"
        val noPKTable = object : Table(tableName) {
            val bar = integer("bar")
        }
        val singlePKTable = object : Table(tableName) {
            val bar = integer("bar")

            override val primaryKey = PrimaryKey(bar)
        }

        val scriptName = "V2__Add_primary_key"
        val scriptDirectory = "src/test/resources"

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            try {
                SchemaUtils.create(noPKTable)

                val script = SchemaUtils.generateMigrationScript(singlePKTable, scriptDirectory = scriptDirectory, scriptName = scriptName)
                assertTrue(script.exists())
                assertEquals("src/test/resources/$scriptName.sql", script.path)

                val expectedStatements: List<String> = SchemaUtils.statementsRequiredForDatabaseMigration(singlePKTable)
                assertEquals(1, expectedStatements.size)

                val fileStatements: List<String> = script.bufferedReader().readLines().map { it.trimEnd(';') }
                expectedStatements.zip(fileStatements).forEach { (expected, actual) ->
                    assertEquals(expected, actual)
                }
            } finally {
                assertTrue(File("$scriptDirectory/$scriptName.sql").delete())
                SchemaUtils.drop(noPKTable)
            }
        }
    }

    @Test
    fun testMigrationScriptOverwrittenIfAlreadyExists() {
        val tableName = "tester"
        val noPKTable = object : Table(tableName) {
            val bar = integer("bar")
        }
        val singlePKTable = object : Table(tableName) {
            val bar = integer("bar")

            override val primaryKey = PrimaryKey(bar)
        }

        val directory = "src/test/resources"
        val name = "V2__Test"

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            try {
                SchemaUtils.create(noPKTable)

                // Create initial script
                val initialScript = File("$directory/$name.sql")
                initialScript.createNewFile()
                val statements = SchemaUtils.statementsRequiredForDatabaseMigration(noPKTable)
                statements.forEach {
                    initialScript.appendText(it)
                }

                // Generate script with the same name of initial script
                val newScript = SchemaUtils.generateMigrationScript(singlePKTable, scriptDirectory = directory, scriptName = name)

                val expectedStatements: List<String> = SchemaUtils.statementsRequiredForDatabaseMigration(singlePKTable)
                assertEquals(1, expectedStatements.size)

                val fileStatements: List<String> = newScript.bufferedReader().readLines().map { it.trimEnd(';') }
                expectedStatements.zip(fileStatements).forEach { (expected, actual) ->
                    assertEquals(expected, actual)
                }
            } finally {
                assertTrue(File("$directory/$name.sql").delete())
                SchemaUtils.drop(noPKTable)
            }
        }
    }

    @Test
    fun testNoTablesPassedWhenGeneratingMigrationScript() {
        withDb {
            expectException<IllegalArgumentException> {
                SchemaUtils.generateMigrationScript(scriptDirectory = "src/test/resources", scriptName = "V2__Test")
            }
        }
    }

    @Test
    fun testAddNewPrimaryKeyOnExistingColumn() {
        val tableName = "tester"
        val noPKTable = object : Table(tableName) {
            val bar = integer("bar")
        }

        val singlePKTable = object : Table(tableName) {
            val bar = integer("bar")

            override val primaryKey = PrimaryKey(bar)
        }

        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            try {
                SchemaUtils.create(noPKTable)
                val primaryKey: PrimaryKeyMetadata? = currentDialectTest.existingPrimaryKeys(singlePKTable)[singlePKTable]
                assertNull(primaryKey)

                val expected = "ALTER TABLE ${tableName.inProperCase()} ADD PRIMARY KEY (${noPKTable.bar.nameInDatabaseCase()})"
                val statements = SchemaUtils.statementsRequiredForDatabaseMigration(singlePKTable)
                assertEquals(expected, statements.single())
            } finally {
                SchemaUtils.drop(noPKTable)
            }
        }
    }

    @Test
    fun `columns with default values that haven't changed shouldn't trigger change`() {
        var table by Delegates.notNull<Table>()
        withDb { testDb ->
            try {
                // MySQL doesn't support default values on text columns, hence excluded
                table = if (testDb != TestDB.MYSQL) {
                    object : Table("varchar_test") {
                        val varchar = varchar("varchar_column", 255).default(" ")
                        val text = text("text_column").default(" ")
                    }
                } else {
                    object : Table("varchar_test") {
                        val varchar = varchar("varchar_column", 255).default(" ")
                    }
                }

                SchemaUtils.create(table)
                val actual = SchemaUtils.statementsRequiredForDatabaseMigration(table)
                assertEqualLists(emptyList(), actual)
            } finally {
                SchemaUtils.drop(table)
            }
        }
    }

    @Test
    fun testCreateTableWithQuotedIdentifiers() {
        val identifiers = listOf("\"IdentifierTable\"", "\"IDentiFierCoLUmn\"")
        val quotedTable = object : Table(identifiers[0]) {
            val column1 = varchar(identifiers[1], 32)
        }

        withDb {
            try {
                SchemaUtils.create(quotedTable)
                assertTrue(quotedTable.exists())

                val statements = SchemaUtils.statementsRequiredForDatabaseMigration(quotedTable)
                assertTrue(statements.isEmpty())
            } finally {
                SchemaUtils.drop(quotedTable)
            }
        }
    }

    @Test
    fun testDropExtraIndexOnSameColumn() {
        val testTableWithTwoIndices = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
            val byName2 = index("test_table_by_name_2", false, name)
        }

        val testTableWithOneIndex = object : Table("test_table") {
            val id = integer("id")
            val name = varchar("name", length = 42)

            override val primaryKey = PrimaryKey(id)
            val byName = index("test_table_by_name", false, name)
        }

        // Oracle does not allow more than one index on a column
        withTables(excludeSettings = listOf(TestDB.ORACLE), tables = arrayOf(testTableWithTwoIndices)) {
            try {
                SchemaUtils.create(testTableWithTwoIndices)
                assertTrue(testTableWithTwoIndices.exists())

                val statements = SchemaUtils.statementsRequiredForDatabaseMigration(testTableWithOneIndex)
                assertEquals(1, statements.size)
            } finally {
                SchemaUtils.drop(testTableWithTwoIndices)
            }
        }
    }
}
