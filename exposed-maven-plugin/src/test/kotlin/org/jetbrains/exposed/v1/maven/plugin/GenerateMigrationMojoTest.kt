package org.jetbrains.exposed.v1.maven.plugin

import org.apache.maven.project.MavenProject
import org.jetbrains.exposed.v1.migration.plugin.core.VersionFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL

class GenerateMigrationMojoTest {

    private lateinit var mojo: GenerateMigrationMojo

    @BeforeEach
    fun setup() {
        mojo = GenerateMigrationMojo()
    }

    @Test
    fun testDefaultValues() {
        assertEquals("V", mojo.filePrefix)
        assertEquals(VersionFormat.TIMESTAMP_ONLY, mojo.fileVersionFormat)
        assertEquals("__", mojo.fileSeparator)
        assertTrue(mojo.useUpperCaseDescription)
        assertEquals(".sql", mojo.fileExtension)
        assertNull(mojo.fullFileName)
        assertNull(mojo.databaseUrl)
        assertNull(mojo.databaseUser)
        assertNull(mojo.databasePassword)
        assertNull(mojo.testContainersImageName)
        assertFalse(mojo.debug)
    }

    @Test
    fun testCustomValues() {
        mojo.tablesPackage = "com.example.tables"
        mojo.fileDirectory = File("/tmp/migrations")
        mojo.filePrefix = "R"
        mojo.fileVersionFormat = VersionFormat.MAJOR_MINOR
        mojo.fileSeparator = "-"
        mojo.useUpperCaseDescription = false
        mojo.fileExtension = ".xml"
        mojo.fullFileName = "all.sql"
        mojo.databaseUrl = "jdbc:h2:mem:test"
        mojo.databaseUser = "sa"
        mojo.databasePassword = ""
        mojo.testContainersImageName = "postgres:13-alpine"
        mojo.debug = true

        assertEquals("com.example.tables", mojo.tablesPackage)
        assertEquals(File("/tmp/migrations"), mojo.fileDirectory)
        assertEquals("R", mojo.filePrefix)
        assertEquals(VersionFormat.MAJOR_MINOR, mojo.fileVersionFormat)
        assertEquals("-", mojo.fileSeparator)
        assertFalse(mojo.useUpperCaseDescription)
        assertEquals(".xml", mojo.fileExtension)
        assertEquals("all.sql", mojo.fullFileName)
        assertEquals("jdbc:h2:mem:test", mojo.databaseUrl)
        assertEquals("sa", mojo.databaseUser)
        assertEquals("", mojo.databasePassword)
        assertEquals("postgres:13-alpine", mojo.testContainersImageName)
        assertTrue(mojo.debug)
    }

    @Test
    fun testClasspathUrlsDerivedFromMavenProject() {
        val mavenProject = object : MavenProject() {
            override fun getRuntimeClasspathElements(): List<String> =
                listOf("/tmp/classes", "/tmp/dep.jar")
        }
        injectProject(mojo, mavenProject)

        assertEquals(
            listOf(
                File("/tmp/classes").toURI().toURL(),
                File("/tmp/dep.jar").toURI().toURL(),
            ),
            readClasspathUrls(mojo),
        )
    }

    private fun injectProject(mojo: GenerateMigrationMojo, project: MavenProject) {
        val field = GenerateMigrationMojo::class.java.getDeclaredField("project")
        field.isAccessible = true
        field.set(mojo, project)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readClasspathUrls(mojo: GenerateMigrationMojo): List<URL> {
        val method = GenerateMigrationMojo::class.java.getDeclaredMethod("getClasspathUrls")
        method.isAccessible = true
        return method.invoke(mojo) as List<URL>
    }
}
