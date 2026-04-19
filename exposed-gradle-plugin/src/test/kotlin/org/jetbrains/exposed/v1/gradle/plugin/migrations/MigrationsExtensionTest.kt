package org.jetbrains.exposed.v1.gradle.plugin.migrations

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.exposed.v1.gradle.plugin.MigrationsExtension
import org.jetbrains.exposed.v1.gradle.plugin.VersionFormat
import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals("V", extension.filePrefix.get())
        Assertions.assertEquals(VersionFormat.TIMESTAMP_ONLY, extension.fileVersionFormat.get())
        Assertions.assertEquals("__", extension.fileSeparator.get())
        assertTrue(extension.useUpperCaseDescription.get())
        Assertions.assertEquals(".sql", extension.fileExtension.get())
    }

    @Test
    fun `test custom values`() {
        extension.tablesPackage.set("com.example.tables")
        extension.fileDirectory.set(project.layout.projectDirectory.dir("custom/migrations"))

        extension.filePrefix.set("R")
        extension.fileVersionFormat.set(VersionFormat.MAJOR_MINOR)
        extension.fileSeparator.set("-")
        extension.useUpperCaseDescription.set(false)
        extension.fileExtension.set(".xml")

        extension.databaseUrl.set("jdbc:h2:mem:test")
        extension.databaseUser.set("sa")
        extension.databasePassword.set("")

        Assertions.assertEquals("com.example.tables", extension.tablesPackage.get())
        Assertions.assertEquals(
            File(project.projectDir, "custom/migrations").absolutePath,
            extension.fileDirectory.get().asFile.absolutePath
        )

        Assertions.assertEquals("R", extension.filePrefix.get())
        Assertions.assertEquals(VersionFormat.MAJOR_MINOR, extension.fileVersionFormat.get())
        Assertions.assertEquals("-", extension.fileSeparator.get())
        assertFalse(extension.useUpperCaseDescription.get())
        Assertions.assertEquals(".xml", extension.fileExtension.get())

        Assertions.assertEquals("jdbc:h2:mem:test", extension.databaseUrl.get())
        Assertions.assertEquals("sa", extension.databaseUser.get())
        Assertions.assertEquals("", extension.databasePassword.get())
    }
}
