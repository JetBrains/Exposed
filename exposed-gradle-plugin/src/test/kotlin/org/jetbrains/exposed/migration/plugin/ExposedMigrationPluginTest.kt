package org.jetbrains.exposed.migration.plugin

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for the ExposedMigrationPlugin class.
 */
class ExposedMigrationPluginTest {

    private lateinit var project: Project

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
        // Skip applying Kotlin JVM plugin as it's not needed for these tests
        project.pluginManager.apply("org.jetbrains.exposed.migration")
    }

    @Test
    fun `test plugin applied`() {
        // Verify that the plugin is applied
        assertTrue(project.plugins.hasPlugin("org.jetbrains.exposed.migration"))
    }

    @Test
    fun `test extension registered`() {
        // Verify that the extension is registered
        val extension = project.extensions.findByName("exposedMigration")
        assertNotNull(extension)
        assertTrue(extension is ExposedMigrationExtension)
    }

    @Test
    fun `test tasks registered`() {
        // Verify that the generateMigrations task is registered
        val task = project.tasks.findByName("generateMigrations")
        assertNotNull(task)
        assertTrue(task is GenerateMigrationsTask)
    }

    @Test
    fun `test task configuration with database properties`() {
        // Get the extension and task
        val extension = project.extensions.getByType(ExposedMigrationExtension::class.java)
        val task = project.tasks.getByName("generateMigrations") as GenerateMigrationsTask

        // Set custom values in the extension
        extension.migrationFilePrefix.set("M")
        extension.migrationFileSeparator.set("_")
        extension.migrationFileExtension.set("migration")
        extension.migrationsDir.set(project.layout.projectDirectory.dir("custom/migrations"))
        extension.exposedTablesPackage.set("com.example.tables")

        // Set database connection properties
        extension.databaseUrl.set("jdbc:h2:mem:test")
        extension.databaseUser.set("sa")
        extension.databasePassword.set("")

        // Force task configuration
        project.tasks.configureEach {}

        // Verify that the task is configured with the extension values
        assertTrue(task.migrationFilePrefix.isPresent)
        assertTrue(task.migrationFileSeparator.isPresent)
        assertTrue(task.migrationFileExtension.isPresent)
        assertTrue(task.migrationsDir.isPresent)
        assertTrue(task.exposedTablesPackage.isPresent)

        // Verify that the database connection properties are correctly passed to the task
        assertTrue(task.databaseUrl.isPresent)
        assertTrue(task.databaseUser.isPresent)
        assertTrue(task.databasePassword.isPresent)
        assertEquals("jdbc:h2:mem:test", task.databaseUrl.get())
        assertEquals("sa", task.databaseUser.get())
        assertEquals("", task.databasePassword.get())

        // Verify that TestContainers and Flyway are disabled by default
        assertFalse(task.testContainersImageName.isPresent)
    }

    @Test
    fun `test task configuration with TestContainers`() {
        // Get the extension and task
        val extension = project.extensions.getByType(ExposedMigrationExtension::class.java)
        val task = project.tasks.getByName("generateMigrations") as GenerateMigrationsTask

        // Set custom values in the extension
        extension.migrationsDir.set(project.layout.projectDirectory.dir("custom/migrations"))
        extension.exposedTablesPackage.set("com.example.tables")

        // Enable TestContainers with custom image
        extension.testContainersImageName.set("postgres:13-alpine")

        // Force task configuration
        project.tasks.configureEach {}

        assertTrue(task.testContainersImageName.isPresent)
        assertEquals("postgres:13-alpine", task.testContainersImageName.get())

        // Database properties should be optional when TestContainers is used
        assertFalse(task.databaseUrl.isPresent)
        assertFalse(task.databaseUser.isPresent)
        assertFalse(task.databasePassword.isPresent)
    }
}
