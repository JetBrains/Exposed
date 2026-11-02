package org.jetbrains.exposed.v1.maven.plugin.integrations

import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.project.MavenProject
import org.jetbrains.exposed.v1.maven.plugin.GenerateMigrationsMojo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * In-process integration test for the Exposed Maven plugin.
 *
 * Exercises the full [GenerateMigrationsMojo.execute] path against a real H2 in-memory database,
 * using the surrounding JVM's classpath as the scanning target. This is intentionally
 * lighter than spawning Maven via maven-invoker; the plugin descriptor itself is verified by
 * the gradlex `maven-plugin-development` build step.
 */
class MigrationsIntegrationTest {

    @field:TempDir
    private lateinit var migrationsDir: File

    @Test
    fun generatesZeroMigrationsForEmptyPackage() {
        val mojo = GenerateMigrationsMojo().apply {
            tablesPackage = "com.example.nonexistent"
            tablesPackages = listOf("com.example.nonexistent")
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
    fun failsWhenTablesPackagesMissing() {
        val mojo = GenerateMigrationsMojo().apply {
            fileDirectory = migrationsDir
            databaseUrl = "jdbc:h2:mem:testDb;DB_CLOSE_DELAY=-1;"
            databaseUser = ""
            databasePassword = ""
        }
        injectProject(mojo, projectWithTestClasspath())

        // Versions 1.3.* to 1.5.* would have failed due to `tablesPackage` property throwing uninitialized property message;
        // Versions 1.6.+ fails if either `tablesPackage` or `tablesPackages` is not set, so descriptive message comes
        // from value state check after consolidating values of both properties; and exception is same type as other
        // invalid configuration exceptions
        val ex = assertThrows<MojoFailureException> {
            mojo.execute()
        }
        assertTrue(
            ex.message?.contains("Package name(s)") == true,
            "Expected message to mention package names, was: ${ex.message}",
        )
    }

    @Test
    fun failsWhenNeitherDatabaseNorTestcontainersConfigured() {
        val mojo = GenerateMigrationsMojo().apply {
            tablesPackage = "com.example.nonexistent"
            fileDirectory = migrationsDir
        }
        injectProject(mojo, projectWithTestClasspath())

        assertThrows<MojoFailureException> {
            mojo.execute()
        }
    }

    private fun projectWithTestClasspath(): MavenProject {
        val cp = System.getProperty("java.class.path").split(File.pathSeparator)
        return object : MavenProject() {
            override fun getRuntimeClasspathElements(): List<String> = cp
        }
    }

    private fun injectProject(mojo: GenerateMigrationsMojo, project: MavenProject) {
        val field = GenerateMigrationsMojo::class.java.getDeclaredField("project")
        field.isAccessible = true
        field.set(mojo, project)
    }
}
