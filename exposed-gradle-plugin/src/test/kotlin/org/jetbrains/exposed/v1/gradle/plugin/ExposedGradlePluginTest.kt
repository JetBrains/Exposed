package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class ExposedGradlePluginTest {
    private lateinit var project: Project

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
        // Skip applying Kotlin JVM plugin as it's not needed for these tests
        project.pluginManager.apply("org.jetbrains.exposed.plugin")
    }

    @Test
    fun `test plugin applied`() {
        // Verify that the plugin is applied
        assertTrue(project.plugins.hasPlugin("org.jetbrains.exposed.plugin"))
    }

    @Test
    fun `test root extension registered`() {
        // Verify that the parent extension is registered
        val extension = project.extensions.findByName(ExposedExtension.NAME)
        assertNotNull(extension)
        assertTrue(extension is ExposedExtension)
    }

    @Test
    fun `test nested extensions registered`() {
        // Verify that any children extensions are registered
        val nestedExtension = project.exposedExtensions.getByName(MigrationsExtension.NAME)
        assertNotNull(nestedExtension)
        assertTrue(nestedExtension is MigrationsExtension)
    }

    @Test
    fun `test all tasks registered`() {
        // Verify that the generateMigrations task is registered
        val task = project.tasks.findByName(GENERATE_MIGRATIONS_TASK_NAME)
        assertNotNull(task)
        assertTrue(task is GenerateMigrationsTask)
        assertEquals(ExposedGradlePlugin.TASK_GROUP, task?.group)
    }

    @Test
    fun `test migrations task configuration with database properties`() {
        val extension = project.exposedExtensions.getByType(MigrationsExtension::class.java)

        // Set custom values in the extension
        extension.tablesPackage.set("com.example.tables")
        extension.fileDirectory.set(project.layout.projectDirectory.dir("custom/migrations"))
        extension.filePrefix.set("M")
        extension.fileVersionFormat.set(VersionFormat.MAJOR_ONLY)
        extension.fileSeparator.set("_")
        extension.useUpperCaseDescription.set(false)
        extension.fileExtension.set(".xml")

        // Set database connection properties
        extension.databaseUrl.set("jdbc:h2:mem:test")
        extension.databaseUser.set("sa")
        extension.databasePassword.set("")

        // Get the task & force task configuration
        val task = project.tasks.getByName(GENERATE_MIGRATIONS_TASK_NAME) as GenerateMigrationsTask
        project.tasks.configureEach {}

        // Verify that the task is configured with the extension values
        assertTrue(task.tablesPackage.isPresent)
        assertTrue(task.fileDirectory.isPresent)
        assertTrue(task.filePrefix.isPresent)
        assertTrue(task.fileVersionFormat.isPresent)
        assertTrue(task.fileSeparator.isPresent)
        assertTrue(task.useUpperCaseDescription.isPresent)
        assertTrue(task.fileExtension.isPresent)
        assertNull(task.fullFileName)

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
    fun `test migrations task configuration with TestContainers`() {
        val extension = project.exposedExtensions.getByType(MigrationsExtension::class.java)

        // Set custom values in the extension
        extension.tablesPackage.set("com.example.tables")
        extension.fileDirectory.set(project.layout.projectDirectory.dir("custom/migrations"))

        // Enable TestContainers with custom image
        extension.testContainersImageName.set("postgres:13-alpine")

        // Get the task & force task configuration
        val task = project.tasks.getByName(GENERATE_MIGRATIONS_TASK_NAME) as GenerateMigrationsTask
        project.tasks.configureEach {}

        // Verify that the TestContainer property is correctly passed to the task
        assertTrue(task.testContainersImageName.isPresent)
        assertEquals("postgres:13-alpine", task.testContainersImageName.get())

        // Verify that database properties are disabled by default
        assertFalse(task.databaseUrl.isPresent)
        assertFalse(task.databaseUser.isPresent)
        assertFalse(task.databasePassword.isPresent)
    }
}
