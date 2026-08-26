package org.jetbrains.exposed.v1.plugin.core.migration.collision

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationConfig
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationGenerator
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationLogger
import org.jetbrains.exposed.v1.plugin.core.migration.VersionFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals

object Parent : Table("issue_2897_parent") {
    val id = integer("id")
    override val primaryKey = PrimaryKey(id)
}

object ChildOne : Table("issue_2897_child_one") {
    val id = integer("id")
    val parentId = integer("parent_id").references(Parent.id)
    override val primaryKey = PrimaryKey(id)
}

object ChildTwo : Table("issue_2897_child_two") {
    val id = integer("id")
    val parentId = integer("parent_id").references(Parent.id)
    override val primaryKey = PrimaryKey(id)
}

object Alpha : Table("issue_2897_alpha") {
    val id = integer("id")
    override val primaryKey = PrimaryKey(id)
}

@OptIn(InternalApi::class)
class MigrationGeneratorCollisionTest {
    @field:TempDir
    private lateinit var migrationsDirectory: File

    @Test
    fun testDependentTablesDoNotOverwriteGeneratedMigrations() {
        val generator = MigrationGenerator(
            config = MigrationConfig(
                tablesPackage = this::class.java.packageName,
                classpathUrls = listOf(this::class.java.protectionDomain.codeSource.location),
                fileDirectory = migrationsDirectory,
                fileVersionFormat = VersionFormat.TIMESTAMP_WITHOUT_SECONDS,
                databaseUrl = "jdbc:h2:mem:${UUID.randomUUID()}",
                databaseUser = "",
                databasePassword = "",
            ),
            logger = object : MigrationLogger {
                override fun lifecycle(message: String) = Unit
                override fun debug(message: String) = Unit
                override val isDebugEnabled: Boolean = false
            },
        )

        val generated = generator.generate()
        val expectedTables = setOf(
            Parent.tableName,
            ChildOne.tableName,
            ChildTwo.tableName,
            Alpha.tableName,
        )
        val generatedFiles = migrationsDirectory.listFiles().orEmpty()
        val createTableRegex = Regex("""CREATE TABLE(?: IF NOT EXISTS)?\s+\"?([\w.]+)\"?""", RegexOption.IGNORE_CASE)
        val createdTables = generatedFiles
            .flatMap { file -> createTableRegex.findAll(file.readText()).map { it.groupValues[1] }.toList() }
            .map { it.substringAfterLast('.').lowercase() }
            .toSet()

        assertEquals(expectedTables.size, generated.size, "All table migrations must be preserved: $generated")
        assertEquals(expectedTables.size, generatedFiles.size)
        assertEquals(expectedTables, createdTables, "All table DDL must be preserved")
    }
}
