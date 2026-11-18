package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.exposed.v1.plugin.core.migration.VersionFormat
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.UuidColumnType
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
    fun testPluginApplied() {
        // Verify that the plugin is applied
        assertTrue(project.plugins.hasPlugin("org.jetbrains.exposed.plugin"))
    }

    @Test
    fun testRootExtensionRegistered() {
        // Verify that the parent extension is registered
        val extension = project.extensions.findByName(ExposedExtension.NAME)
        assertNotNull(extension)
        assertTrue(extension is ExposedExtension)
    }

    @Test
    fun testNestedExtensionsRegistered() {
        // Verify that any children extensions are registered
        val nestedExtension = project.exposedExtensions.getByName(MigrationsExtension.NAME)
        assertNotNull(nestedExtension)
        assertTrue(nestedExtension is MigrationsExtension)

        val tablesExtension = project.exposedExtensions.getByName(TablesExtension.NAME)
        assertNotNull(tablesExtension)
        assertTrue(tablesExtension is TablesExtension)
    }

    @Test
    fun testAllTasksRegistered() {
        // Verify that the generateMigrations task is registered
        val task = project.tasks.findByName(GENERATE_MIGRATIONS_TASK_NAME)
        assertNotNull(task)
        assertTrue(task is GenerateMigrationsTask)
        assertEquals(ExposedGradlePlugin.TASK_GROUP, task?.group)

        // Verify that the generateTables task is registered
        val tablesTask = project.tasks.findByName(GENERATE_TABLES_TASK_NAME)
        assertNotNull(tablesTask)
        assertTrue(tablesTask is GenerateTablesTask)
        assertEquals(ExposedGradlePlugin.TASK_GROUP, tablesTask?.group)
    }

    @Test
    fun testMigrationsTaskConfigurationWithDatabaseProperties() {
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
    fun testMigrationsTaskConfigurationWithTestContainers() {
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

    @Test
    fun testMigrationsTaskConfigurationWithMultiplePackages() {
        val extension = project.exposedExtensions.getByType(MigrationsExtension::class.java)
        val allPackages = listOf("com.example1.tables", "com.example2.tables")

        // Set custom values in the extension
        extension.tablesPackages.set(allPackages)
        extension.fileDirectory.set(project.layout.projectDirectory.dir("custom/migrations"))
        extension.testContainersImageName.set("postgres:13-alpine")

        // Get the task & force task configuration
        val task = project.tasks.getByName(GENERATE_MIGRATIONS_TASK_NAME) as GenerateMigrationsTask
        project.tasks.configureEach {}

        // Verify that the task is configured with the expected extension values
        assertTrue(task.tablesPackages.isPresent)
        assertEquals(allPackages, task.tablesPackages.get())
        assertFalse(task.tablesPackage.isPresent)
    }

    @Test
    fun testTablesTaskConfigurationWithDatabaseProperties() {
        val extension = project.exposedExtensions.getByType(TablesExtension::class.java)

        // Set custom values in the extension
        extension.tablesPackage.set("com.example.tables")
        extension.fileDirectory.set(project.layout.projectDirectory.dir("custom/generated"))
        extension.schemas.set(listOf("PUBLIC", "SALES*"))
        extension.objectNameSuffix.set("Table")
        extension.uuidColumnType.set(UuidColumnType.JAVA)
        extension.dateTimeColumnType.set(DateTimeColumnType.JAVA_TIME)
        extension.classpath.setFrom(project.layout.projectDirectory.dir("libs"))
        extension.generateIdTables.set(true)

        // Set database connection properties
        extension.databaseUrl.set("jdbc:h2:mem:test")
        extension.databaseUser.set("sa")
        extension.databasePassword.set("")

        // Get the task & force task configuration
        val task = project.tasks.getByName(GENERATE_TABLES_TASK_NAME) as GenerateTablesTask
        project.tasks.configureEach {}

        // Verify that the task is configured with the extension values
        assertTrue(task.tablesPackage.isPresent)
        assertEquals("com.example.tables", task.tablesPackage.get())
        assertEquals(DateTimeColumnType.JAVA_TIME, task.dateTimeColumnType.get())
        assertEquals(listOf(project.layout.projectDirectory.dir("libs").asFile), task.classpath.files.toList())
        assertTrue(task.fileDirectory.isPresent)
        assertEquals(
            project.layout.projectDirectory.dir("custom/generated").asFile.absolutePath,
            task.fileDirectory.get().asFile.absolutePath
        )
        assertEquals(listOf("PUBLIC", "SALES*"), task.schemas.get())
        assertEquals("Table", task.objectNameSuffix.get())
        assertEquals(UuidColumnType.JAVA, task.uuidColumnType.get())
        assertTrue(task.generateIdTables.get())
        assertNull(task.fullFileName)

        // Verify that the database connection properties are correctly passed to the task
        assertEquals("jdbc:h2:mem:test", task.databaseUrl.get())
        assertEquals("sa", task.databaseUser.get())
        assertEquals("", task.databasePassword.get())
    }

    @Test
    fun testTablesTaskDefaultValues() {
        val task = project.tasks.getByName(GENERATE_TABLES_TASK_NAME) as GenerateTablesTask
        project.tasks.configureEach {}

        assertEquals(
            project.layout.projectDirectory.dir("src/main/kotlin").asFile.absolutePath,
            task.fileDirectory.get().asFile.absolutePath
        )
        assertEquals(emptyList<String>(), task.schemas.get())
        assertEquals("", task.objectNameSuffix.get())
        assertEquals(UuidColumnType.KOTLIN, task.uuidColumnType.get())
        // detection happens at execution time, so the task carries no module by default
        assertFalse(task.dateTimeColumnType.isPresent)
        assertFalse(task.generateIdTables.get())
    }
}
