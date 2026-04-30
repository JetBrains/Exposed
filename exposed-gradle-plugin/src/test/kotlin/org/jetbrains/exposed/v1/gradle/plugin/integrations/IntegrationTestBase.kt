package org.jetbrains.exposed.v1.gradle.plugin.integrations

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.exposed.v1.gradle.plugin.ExposedGradlePlugin
import org.jetbrains.exposed.v1.gradle.plugin.GENERATE_MIGRATIONS_TASK_NAME
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains

abstract class IntegrationTestBase {

    @field:TempDir
    private lateinit var projectDir: File

    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }
    protected val buildFile by lazy { projectDir.resolve("build.gradle.kts") }

    @BeforeEach
    fun setup() {
        settingsFile.writeText(
            """
            pluginManagement {
                repositories {
                    mavenLocal {
                        content { includeGroup("org.jetbrains.exposed.plugin") }
                    }
                    gradlePluginPortal()
                }
            }

            rootProject.name = "test"
            """.trimIndent()
        )
        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("org.jetbrains.exposed.plugin") version "${ExposedGradlePlugin.VERSION}"
            }

            $REPOSITORIES
            """.trimIndent()
        )
    }

    protected fun File.overwriteFile(vararg codeBlocks: String) {
        writeText(codeBlocks.joinToString("\n\n") { it.trimIndent() })
    }

    protected fun File.appendToFile(vararg codeBlocks: String) {
        appendText(codeBlocks.joinToString(separator = "\n\n", prefix = "\n\n") { it.trimIndent() })
    }

    private val runner by lazy {
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
    }

    protected fun runBuild(vararg args: String, configure: GradleRunner.() -> Unit = {}): BuildResult {
        return runner.apply(configure).withArguments(args.asList() + "--stacktrace").build()
    }

    protected fun runBuildAndFail(vararg args: String, configure: GradleRunner.() -> Unit = {}): BuildResult {
        return runner.apply(configure).withArguments(args.asList() + "--stacktrace").buildAndFail()
    }

    companion object {
        const val KOTLIN_VERSION = "2.2.20"

        const val REPOSITORIES = """
            repositories {
                mavenCentral()
            }
        """

        const val PRINT_EXPOSED_TASKS = """
            afterEvaluate {
                val tasks = tasks.filter { it.group == "${ExposedGradlePlugin.TASK_GROUP}" }
                println("Exposed tasks: " + tasks.map { it.name }.sorted())
            }
        """

        val allTasks = setOf(GENERATE_MIGRATIONS_TASK_NAME)

        fun BuildResult.assertExposedTasksAdded(tasks: Set<String>) {
            assertContains(output.lines(), "Exposed tasks: " + tasks.sorted())
        }
    }
}
