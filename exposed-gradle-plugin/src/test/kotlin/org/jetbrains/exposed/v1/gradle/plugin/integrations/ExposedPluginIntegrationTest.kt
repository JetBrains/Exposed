package org.jetbrains.exposed.v1.gradle.plugin.integrations

import org.jetbrains.exposed.v1.gradle.plugin.ExposedGradlePlugin
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ExposedPluginIntegrationTest : IntegrationTestBase() {
    @Test
    fun `kotlin plugin not applied`() {
        buildFile.overwriteFile(
            """
            plugins {
                id("org.jetbrains.exposed.plugin") version "${ExposedGradlePlugin.VERSION}"
            }
            """
        )

        val result = runBuild()
        assertContains(result.output, "Warning: The Exposed Gradle plugin requires the Kotlin Gradle plugin.")
    }

    @Test
    fun `exposed plugin applied after kotlin plugin`() {
        buildFile.appendToFile(
            PRINT_EXPOSED_TASKS
        )

        val result = runBuild()
        result.assertExposedTasksAdded(allTasks)
    }

    @Test
    fun `exposed plugin applied before kotlin plugin`() {
        buildFile.overwriteFile(
            """
            plugins {
                id("org.jetbrains.exposed.plugin") version "${ExposedGradlePlugin.VERSION}"
                kotlin("jvm") version "$KOTLIN_VERSION"
            }
            """,
            REPOSITORIES,
            PRINT_EXPOSED_TASKS
        )

        val result = runBuild()
        result.assertExposedTasksAdded(allTasks)
    }
}
