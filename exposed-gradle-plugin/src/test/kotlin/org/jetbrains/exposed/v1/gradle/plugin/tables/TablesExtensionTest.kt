package org.jetbrains.exposed.v1.gradle.plugin.tables

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.exposed.v1.gradle.plugin.TablesExtension
import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.UuidColumnType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TablesExtensionTest {
    private lateinit var project: Project
    private lateinit var extension: TablesExtension

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
        extension = project.extensions.create(TablesExtension.NAME, TablesExtension::class.java)
    }

    @Test
    fun testDefaultValues() {
        assertFalse(extension.tablesPackage.isPresent)
        assertFalse(extension.fileDirectory.isPresent)
        Assertions.assertEquals(emptyList<String>(), extension.schemas.get())
        Assertions.assertEquals("", extension.objectNameSuffix.get())
        Assertions.assertEquals(UuidColumnType.KOTLIN, extension.uuidColumnType.get())
        assertFalse(extension.dateTimeColumnType.isPresent)
        assertTrue(extension.classpath.isEmpty)
        assertFalse(extension.generateIdTables.get())
        Assertions.assertEquals(emptyList<ColumnMapping>(), extension.columnMappings.get())
        assertFalse(extension.databaseUrl.isPresent)
        assertFalse(extension.databaseUser.isPresent)
        assertFalse(extension.databasePassword.isPresent)
    }

    @Test
    fun testColumnMappingsBlock() {
        // build scripts get the receiver form (`code = ...`) from the Gradle Kotlin DSL, so plain Kotlin sees the Action parameter
        extension.columnMappings { mappings ->
            mappings.column("EVENTS.CREATED_AT") { it.code = "timestamp(\"CREATED_AT\")" }
            mappings.column("EVENTS.PAYLOAD") {
                it.code = "json<Payload>(\"PAYLOAD\", Json.Default)"
                it.imports = listOf("com.example.Payload", "kotlinx.serialization.json.Json")
            }
        }

        Assertions.assertEquals(
            listOf(
                ColumnMapping("EVENTS.CREATED_AT", "timestamp(\"CREATED_AT\")"),
                ColumnMapping("EVENTS.PAYLOAD", "json<Payload>(\"PAYLOAD\", Json.Default)", listOf("com.example.Payload", "kotlinx.serialization.json.Json")),
            ),
            extension.columnMappings.get()
        )

        // a mapping without code is a configuration error, reported when the block is evaluated
        val exception = assertFailsWith<IllegalArgumentException> {
            extension.columnMappings { mappings -> mappings.column("EVENTS.ID") { it.imports = listOf("com.example.Id") } }
        }
        Assertions.assertEquals("The column mapping for 'EVENTS.ID' must set code", exception.message)
    }

    @Test
    fun testCustomValues() {
        extension.tablesPackage.set("com.example.tables")
        extension.fileDirectory.set(project.layout.projectDirectory.dir("custom/generated"))
        extension.schemas.set(listOf("PUBLIC", "SALES*"))
        extension.objectNameSuffix.set("Table")
        extension.uuidColumnType.set(UuidColumnType.JAVA)
        extension.dateTimeColumnType.set(DateTimeColumnType.JAVA_TIME)
        extension.classpath.setFrom(project.layout.projectDirectory.dir("libs"))
        extension.generateIdTables.set(true)

        extension.databaseUrl.set("jdbc:h2:mem:test")
        extension.databaseUser.set("sa")
        extension.databasePassword.set("")

        Assertions.assertEquals("com.example.tables", extension.tablesPackage.get())
        Assertions.assertEquals(DateTimeColumnType.JAVA_TIME, extension.dateTimeColumnType.get())
        Assertions.assertEquals(listOf(File(project.projectDir, "libs")), extension.classpath.files.toList())
        Assertions.assertEquals(
            File(project.projectDir, "custom/generated").absolutePath,
            extension.fileDirectory.get().asFile.absolutePath
        )
        Assertions.assertEquals(listOf("PUBLIC", "SALES*"), extension.schemas.get())
        Assertions.assertEquals("Table", extension.objectNameSuffix.get())
        Assertions.assertEquals(UuidColumnType.JAVA, extension.uuidColumnType.get())
        assertTrue(extension.generateIdTables.get())

        Assertions.assertEquals("jdbc:h2:mem:test", extension.databaseUrl.get())
        Assertions.assertEquals("sa", extension.databaseUser.get())
        Assertions.assertEquals("", extension.databasePassword.get())
    }
}
