package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationsExtensionTest {
    private lateinit var project: Project
    private lateinit var extension: MigrationsExtension

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
        extension = project.extensions.create(MigrationsExtension.NAME, MigrationsExtension::class.java)
    }

    @Test
    fun `test default values`() {
        assertEquals("V", extension.filePrefix.get())
        assertEquals("__", extension.fileSeparator.get())
        assertTrue(extension.useUpperCaseDescription.get())
        assertEquals(".sql", extension.fileExtension.get())
    }

    @Test
    fun `test custom values`() {
        extension.tablesPackage.set("com.example.tables")
        extension.fileDirectory.set(project.layout.projectDirectory.dir("custom/migrations"))

        extension.filePrefix.set("R")
        extension.fileSeparator.set("-")
        extension.useUpperCaseDescription.set(false)
        extension.fileExtension.set(".xml")

        extension.databaseUrl.set("jdbc:h2:mem:test")
        extension.databaseUser.set("sa")
        extension.databasePassword.set("")

        assertEquals("com.example.tables", extension.tablesPackage.get())
        assertEquals(
            File(project.projectDir, "custom/migrations").absolutePath,
            extension.fileDirectory.get().asFile.absolutePath
        )

        assertEquals("R", extension.filePrefix.get())
        assertEquals("-", extension.fileSeparator.get())
        assertFalse(extension.useUpperCaseDescription.get())
        assertEquals(".xml", extension.fileExtension.get())

        assertEquals("jdbc:h2:mem:test", extension.databaseUrl.get())
        assertEquals("sa", extension.databaseUser.get())
        assertEquals("", extension.databasePassword.get())
    }
}
