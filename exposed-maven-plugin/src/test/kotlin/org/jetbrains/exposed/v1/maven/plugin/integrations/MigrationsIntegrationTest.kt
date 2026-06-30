package org.jetbrains.exposed.v1.maven.plugin.integrations

import org.apache.maven.project.MavenProject
import org.jetbrains.exposed.v1.maven.plugin.GenerateMigrationMojo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * In-process integration test for the Exposed Maven plugin.
 *
 * Exercises the full [GenerateMigrationMojo.execute] path against a real H2 in-memory database,
 * using the surrounding JVM's classpath as the scanning target. This is intentionally
 * lighter than spawning Maven via maven-invoker; the plugin descriptor itself is verified by
 * the gradlex `maven-plugin-development` build step.
 */
class MigrationsIntegrationTest {

    @field:TempDir
    private lateinit var migrationsDir: File

    @Test
    fun generatesZeroMigrationsForEmptyPackage() {
        val mojo = GenerateMigrationMojo().apply {
            tablesPackage = "com.example.nonexistent"
            fileDirectory = migrationsDir
            databaseUrl = "jdbc:h2:mem:testDb;DB_CLOSE_DELAY=-1;"
            databaseUser = ""
            databasePassword = ""
        }
        injectProject(mojo, projectWithTestClasspath())

        mojo.execute()

        val files = migrationsDir.listFiles().orEmpty()
        assertEquals(
            0,
            files.size,
            "No migration files expected, got ${files.map { it.name }}",
        )
    }

    @Test
    fun failsWhenTablesPackageMissing() {
        val mojo = GenerateMigrationMojo().apply {
            fileDirectory = migrationsDir
            databaseUrl = "jdbc:h2:mem:testDb;DB_CLOSE_DELAY=-1;"
            databaseUser = ""
            databasePassword = ""
        }
        injectProject(mojo, projectWithTestClasspath())

        val ex = assertThrows<UninitializedPropertyAccessException> {
            mojo.execute()
        }
        assertTrue(
            ex.message?.contains("tablesPackage") == true,
            "Expected message to mention tablesPackage, was: ${ex.message}",
        )
    }

    @Test
    fun failsWhenNeitherDatabaseNorTestcontainersConfigured() {
        val mojo = GenerateMigrationMojo().apply {
            tablesPackage = "com.example.nonexistent"
            fileDirectory = migrationsDir
        }
        injectProject(mojo, projectWithTestClasspath())

        assertThrows<IllegalArgumentException> {
            mojo.execute()
        }
    }

    private fun projectWithTestClasspath(): MavenProject {
        val cp = System.getProperty("java.class.path").split(File.pathSeparator)
        return object : MavenProject() {
            override fun getRuntimeClasspathElements(): List<String> = cp
        }
    }

    private fun injectProject(mojo: GenerateMigrationMojo, project: MavenProject) {
        val field = GenerateMigrationMojo::class.java.getDeclaredField("project")
        field.isAccessible = true
        field.set(mojo, project)
    }
}
