package org.jetbrains.exposed.v1.gradle.plugin.integrations

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.exposed.v1.gradle.plugin.GENERATE_MIGRATIONS_TASK_NAME
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MigrationsIntegrationTest : IntegrationTestBase() {
    @Test
    fun minimumPluginConfigurationNotApplied() {
        val result = runBuildAndFail(GENERATE_MIGRATIONS_TASK_NAME)
        assertEquals(TaskOutcome.FAILED, result.task(":${GENERATE_MIGRATIONS_TASK_NAME}")?.outcome)
        // Versions 1.3.* to 1.5.* would have failed due to `tablesPackage` property throwing generic non-optional message;
        // Versions 1.6.+ fails if either `tablesPackage` or `tablesPackages` is not set, so descriptive message comes
        // from value state check after consolidating values of both properties; and exception is same type as other
        // invalid configuration exceptions
        assertContains(result.output, "Package name(s) for Exposed table definitions must be set")
    }

    @Test
    fun minimumPluginConfigurationsForDatabaseApplied01() {
        buildFile.appendToFile(
            """
                exposed {
                    migrations {
                        tablesPackage.set("com.example.db.tables")
                        databaseUrl.set("jdbc:h2:mem:testDb;DB_CLOSE_DELAY=-1;")
                        databaseUser.set("")
                        databasePassword.set("")
                    }
                }
            """
        )

        val result = runBuild(GENERATE_MIGRATIONS_TASK_NAME)
        assertEquals(TaskOutcome.SUCCESS, result.task(":${GENERATE_MIGRATIONS_TASK_NAME}")?.outcome)
        assertContains(result.output, "# Exposed Migrations Generated 0 migrations:")
    }

    @Test
    fun minimumPluginConfigurationsForDatabaseApplied02() {
        buildFile.appendToFile(
            """
                exposed {
                    migrations {
                        tablesPackages.set(listOf("com.example.db.tables"))
                        databaseUrl.set("jdbc:h2:mem:testDb;DB_CLOSE_DELAY=-1;")
                        databaseUser.set("")
                        databasePassword.set("")
                    }
                }
            """
        )

        val result = runBuild(GENERATE_MIGRATIONS_TASK_NAME)
        assertEquals(TaskOutcome.SUCCESS, result.task(":${GENERATE_MIGRATIONS_TASK_NAME}")?.outcome)
        assertContains(result.output, "# Exposed Migrations Generated 0 migrations:")
    }

    @Test
    fun minimumPluginConfigurationForDatabaseNotApplied() {
        buildFile.appendToFile(
            """
                exposed {
                    migrations {
                        tablesPackages.set(listOf("com.example.db.tables"))
                    }
                }
            """
        )

        val result = runBuildAndFail(GENERATE_MIGRATIONS_TASK_NAME)
        assertEquals(TaskOutcome.FAILED, result.task(":${GENERATE_MIGRATIONS_TASK_NAME}")?.outcome)
        assertContains(result.output, "Database properties (url, user, password) must be provided when not using TestContainers")
    }

    @Test
    fun commandLineArgumentAppearsOnHelp() {
        val result = runBuild("help", "--task=${GENERATE_MIGRATIONS_TASK_NAME}")
        assertContains(result.output, "--filename")
        assertContains(result.output, "The exact filename to use when generating a single script")
    }
}
