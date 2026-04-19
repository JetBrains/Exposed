package org.jetbrains.exposed.v1.gradle.plugin.integrations

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.exposed.v1.gradle.plugin.GENERATE_MIGRATIONS_TASK_NAME
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MigrationsIntegrationTest : IntegrationTestBase() {
    @Test
    fun `minimum plugin configuration not applied`() {
        val result = runBuildAndFail(GENERATE_MIGRATIONS_TASK_NAME)
        assertEquals(TaskOutcome.FAILED, result.task(":${GENERATE_MIGRATIONS_TASK_NAME}")?.outcome)
        assertContains(result.output, "This property isn't marked as optional and no value has been configured")
    }

    @Test
    fun `minimum plugin configurations for database applied`() {
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
    fun `command line argument appears on help`() {
        val result = runBuild("help", "--task=${GENERATE_MIGRATIONS_TASK_NAME}")
        assertContains(result.output, "--filename")
        assertContains(result.output, "The exact filename to use when generating a single script")
    }
}
