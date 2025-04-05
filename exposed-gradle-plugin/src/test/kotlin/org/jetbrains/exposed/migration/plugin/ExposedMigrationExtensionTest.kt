package org.jetbrains.exposed.migration.plugin

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ExposedMigrationExtensionTest {

    private lateinit var project: Project
    private lateinit var extension: ExposedMigrationExtension

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
        extension = project.extensions.create("exposedMigration", ExposedMigrationExtension::class.java)
    }

    @Test
    fun `test default values`() {
        assertEquals("V", extension.migrationFilePrefix.get())
        assertEquals("__", extension.migrationFileSeparator.get())
        assertEquals(".sql", extension.migrationFileExtension.get())
    }

    @Test
    fun `test custom values`() {
        extension.migrationFilePrefix.set("M")
        extension.migrationFileSeparator.set("_")
        extension.migrationFileExtension.set("migration")
        extension.migrationsDir.set(project.layout.projectDirectory.dir("custom/migrations"))
        extension.exposedTablesPackage.set("com.example.tables")

        extension.databaseUrl.set("jdbc:h2:mem:test")
        extension.databaseUser.set("sa")
        extension.databasePassword.set("")

        assertEquals("M", extension.migrationFilePrefix.get())
        assertEquals("_", extension.migrationFileSeparator.get())
        assertEquals("migration", extension.migrationFileExtension.get())
        assertEquals(
            File(project.projectDir, "custom/migrations").absolutePath,
            extension.migrationsDir.get().asFile.absolutePath
        )
        assertEquals("com.example.tables", extension.exposedTablesPackage.get())

        assertEquals("jdbc:h2:mem:test", extension.databaseUrl.get())
        assertEquals("sa", extension.databaseUser.get())
        assertEquals("", extension.databasePassword.get())
    }
}
