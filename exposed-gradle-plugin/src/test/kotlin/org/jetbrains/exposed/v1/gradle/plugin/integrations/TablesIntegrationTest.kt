package org.jetbrains.exposed.v1.gradle.plugin.integrations

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.exposed.v1.gradle.plugin.GENERATE_TABLES_TASK_NAME
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TablesIntegrationTest : IntegrationTestBase() {
    @Test
    fun minimumPluginConfigurationNotApplied() {
        val result = runBuildAndFail(GENERATE_TABLES_TASK_NAME)
        assertEquals(TaskOutcome.FAILED, result.task(":$GENERATE_TABLES_TASK_NAME")?.outcome)
        assertContains(result.output, "Package name for generated Exposed table definitions must be set")
    }

    @Test
    fun minimumPluginConfigurationForDatabaseNotApplied() {
        buildFile.appendToFile(
            """
                exposed {
                    tables {
                        tablesPackage.set("com.example.db.tables")
                    }
                }
            """
        )

        val result = runBuildAndFail(GENERATE_TABLES_TASK_NAME)
        assertEquals(TaskOutcome.FAILED, result.task(":$GENERATE_TABLES_TASK_NAME")?.outcome)
        assertContains(result.output, "Database properties (url, user, password) must be provided")
    }

    @Test
    fun minimumPluginConfigurationsForDatabaseApplied() {
        val databaseUrl = createFileDatabase("CREATE TABLE USERS (ID INT AUTO_INCREMENT PRIMARY KEY, EMAIL VARCHAR(320) NOT NULL)")
        buildFile.appendToFile(
            """
                exposed {
                    tables {
                        tablesPackage.set("com.example.db.tables")
                        databaseUrl.set("$databaseUrl")
                        databaseUser.set("")
                        databasePassword.set("")
                    }
                }
            """
        )

        val result = runBuild(GENERATE_TABLES_TASK_NAME)
        assertEquals(TaskOutcome.SUCCESS, result.task(":$GENERATE_TABLES_TASK_NAME")?.outcome)
        assertContains(result.output, "# Exposed Tables Generated 1 table objects in 1 file(s):")

        val generatedFile = projectDir.resolve("src/main/kotlin/com/example/db/tables/Users.kt")
        assertTrue(generatedFile.isFile, "Expected generated file at ${generatedFile.absolutePath}")
        val source = generatedFile.readText()
        assertContains(source, "package com.example.db.tables")
        assertContains(source, "object Users : Table(\"USERS\") {")
        assertContains(source, "integer(\"ID\").autoIncrement()")
        assertContains(source, "varchar(\"EMAIL\", 320)")
        assertContains(source, "override val primaryKey: Table.PrimaryKey = PrimaryKey(id)")
    }

    @Test
    fun customFileDirectoryAndSingleFileNameApplied() {
        val databaseUrl = createFileDatabase(
            "CREATE TABLE USERS (ID INT NOT NULL)",
            "CREATE TABLE CITIES (ID INT NOT NULL)",
        )
        buildFile.appendToFile(
            """
                exposed {
                    tables {
                        tablesPackage.set("com.example.db.tables")
                        fileDirectory.set(layout.projectDirectory.dir("generated"))
                        databaseUrl.set("$databaseUrl")
                        databaseUser.set("")
                        databasePassword.set("")
                    }
                }
            """
        )

        val result = runBuild(GENERATE_TABLES_TASK_NAME, "--filename=Schema.kt")
        assertEquals(TaskOutcome.SUCCESS, result.task(":$GENERATE_TABLES_TASK_NAME")?.outcome)
        assertContains(result.output, "# Exposed Tables Generated 2 table objects in 1 file(s):")

        val generatedFile = projectDir.resolve("generated/com/example/db/tables/Schema.kt")
        assertTrue(generatedFile.isFile, "Expected generated file at ${generatedFile.absolutePath}")
        val source = generatedFile.readText()
        assertContains(source, "object Cities : Table(\"CITIES\")")
        assertContains(source, "object Users : Table(\"USERS\")")
    }

    @Test
    fun existingFileInTargetPackageFailsTheTask() {
        val databaseUrl = createFileDatabase("CREATE TABLE USERS (ID INT NOT NULL)")
        val existingFile = projectDir.resolve("src/main/kotlin/com/example/db/tables/Users.kt")
        existingFile.parentFile.mkdirs()
        val existingSource = "package com.example.db.tables\n\nobject Users\n"
        existingFile.writeText(existingSource)
        buildFile.appendToFile(
            """
                exposed {
                    tables {
                        tablesPackage.set("com.example.db.tables")
                        databaseUrl.set("$databaseUrl")
                        databaseUser.set("")
                        databasePassword.set("")
                    }
                }
            """
        )

        val result = runBuildAndFail(GENERATE_TABLES_TASK_NAME)
        assertEquals(TaskOutcome.FAILED, result.task(":$GENERATE_TABLES_TASK_NAME")?.outcome)
        assertContains(result.output, "generateTables never overwrites existing files")
        assertContains(result.output, "already declares 'Users'. Move or rename the file")
        assertEquals(existingSource, existingFile.readText(), "The existing file must be left untouched")
    }

    @Test
    fun columnMappingsAreAppliedToGeneratedFiles() {
        val databaseUrl = createFileDatabase(
            "CREATE TABLE EVENTS (ID INT NOT NULL PRIMARY KEY, CREATED_AT TIMESTAMP NOT NULL, SPAN INTERVAL DAY NULL)"
        )
        buildFile.appendToFile(
            """
                exposed {
                    tables {
                        tablesPackage.set("com.example.db.tables")
                        databaseUrl.set("$databaseUrl")
                        databaseUser.set("")
                        databasePassword.set("")
                        columnMappings {
                            column("events.created_at") { code = "timestamp(\"CREATED_AT\")" }
                            column("EVENTS.SPAN") {
                                code = "registerColumn(\"SPAN\", DayIntervalColumnType())"
                                imports = listOf("com.example.db.DayIntervalColumnType")
                            }
                        }
                    }
                }
            """
        )

        val result = runBuild(GENERATE_TABLES_TASK_NAME)
        assertEquals(TaskOutcome.SUCCESS, result.task(":$GENERATE_TABLES_TASK_NAME")?.outcome)

        val source = projectDir.resolve("src/main/kotlin/com/example/db/tables/Events.kt").readText()
        // the mapped code replaces the generated call, the type is inferred, and the modifiers are still derived
        assertContains(source, "val createdAt = timestamp(\"CREATED_AT\")")
        assertContains(source, "val span = registerColumn(\"SPAN\", DayIntervalColumnType()).nullable()")
        assertContains(source, "import com.example.db.DayIntervalColumnType")
        assertContains(source, "import org.jetbrains.exposed.v1.datetime.timestamp")
        assertContains(source, "Mapped by the columnMappings configuration (key 'events.created_at')")
        assertFalse(source.contains("TODO"), "The mapped column must not be reported as unsupported: $source")
    }

    @Test
    fun commandLineArgumentAppearsOnHelp() {
        val result = runBuild("help", "--task=$GENERATE_TABLES_TASK_NAME")
        assertContains(result.output, "--filename")
        assertContains(result.output, "The exact filename to use when generating a single file for all tables")
    }

    private fun createFileDatabase(vararg statements: String): String {
        val databaseFile = projectDir.resolve("data/testdb")
        val databaseUrl = "jdbc:h2:file:${databaseFile.absolutePath.replace('\\', '/')}"
        DriverManager.getConnection(databaseUrl, "", "").use { connection ->
            connection.createStatement().use { statement ->
                statements.forEach { statement.execute(it) }
            }
        }
        return databaseUrl
    }
}
