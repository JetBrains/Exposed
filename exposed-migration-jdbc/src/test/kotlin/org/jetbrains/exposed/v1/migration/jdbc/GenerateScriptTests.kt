package org.jetbrains.exposed.v1.migration.jdbc

import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalDatabaseMigrationApi::class)
class GenerateScriptTests : DatabaseTestsBase() {
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

        withTables(excludeSettings = listOf(TestDB.SQLITE), noPKTable) {
            val script = MigrationUtils.generateMigrationScript(singlePKTable, scriptDirectory = scriptDirectory, scriptName = scriptName, withLogs = false)
            assertTrue(script.exists())
            assertEquals("src${File.separator}test${File.separator}resources${File.separator}$scriptName.sql", script.path)

            val expectedStatements: List<String> = MigrationUtils.statementsRequiredForDatabaseMigration(singlePKTable, withLogs = false)
            assertEquals(1, expectedStatements.size)

            val fileStatements: List<String> = script.bufferedReader().readLines().map { it.trimEnd(';') }
            expectedStatements.zip(fileStatements).forEach { (expected, actual) ->
                assertEquals(expected, actual)
            }

            assertTrue(File("$scriptDirectory/$scriptName.sql").delete())
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

        withTables(excludeSettings = listOf(TestDB.SQLITE), noPKTable) {
            // Create initial script
            val initialScript = File("$directory/$name.sql")
            initialScript.createNewFile()
            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(noPKTable, withLogs = false)
            statements.forEach {
                initialScript.appendText(it)
            }

            // Generate script with the same name of initial script
            val newScript = MigrationUtils.generateMigrationScript(singlePKTable, scriptDirectory = directory, scriptName = name, withLogs = false)

            val expectedStatements: List<String> = MigrationUtils.statementsRequiredForDatabaseMigration(singlePKTable, withLogs = false)
            assertEquals(1, expectedStatements.size)

            val fileStatements: List<String> = newScript.bufferedReader().readLines().map { it.trimEnd(';') }
            expectedStatements.zip(fileStatements).forEach { (expected, actual) ->
                assertEquals(expected, actual)
            }

            assertTrue(File("$directory/$name.sql").delete())
        }
    }

    @Test
    fun testNoTablesPassedWhenGeneratingMigrationScript() {
        withDb {
            expectException<IllegalArgumentException> {
                MigrationUtils.generateMigrationScript(scriptDirectory = "src/test/resources", scriptName = "V2__Test", withLogs = false)
            }
        }
    }
}
