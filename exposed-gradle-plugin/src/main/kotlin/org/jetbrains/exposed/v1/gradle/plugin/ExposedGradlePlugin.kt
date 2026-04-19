package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A plugin extension of the Gradle build tool that applies configured automations to specific Exposed functionality.
 */
class ExposedGradlePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        extensions
            .create(ExposedExtension.NAME, ExposedExtension::class.java, objects)

        configureMigrations()

        var kotlinPluginApplied = false
        whenKotlinPluginApplied {
            kotlinPluginApplied = true
        }

        afterEvaluate {
            if (!kotlinPluginApplied) {
                reportKotlinPluginMissingWarning()
                return@afterEvaluate
            }
        }
    }

    private fun Project.reportKotlinPluginMissingWarning() {
        logger.warn("Warning: The Exposed Gradle plugin requires the Kotlin Gradle plugin.")
        logger.lifecycle(
            """
            |To fix this, apply the Kotlin Gradle plugin to your project:
            |
            |  plugins {
            |      kotlin("jvm")
            |  }
            |
            """.trimMargin()
        )
    }

    companion object {
        /** The Exposed plugin version, which should be equal to the Exposed version used in a project. */
        const val VERSION: String = "1.2.0"

        /** The group name used for Exposed tasks. */
        const val TASK_GROUP: String = "Exposed"
    }
}

private fun Project.whenKotlinPluginApplied(block: (KotlinPluginType) -> Unit) {
    whenKotlinJvmApplied { block(KotlinPluginType.JVM) }
}

private fun Project.whenKotlinJvmApplied(block: () -> Unit) {
    pluginManager.withPlugin(KOTLIN_JVM_PLUGIN_ID) { block() }
}

private enum class KotlinPluginType {
    JVM,
}

private const val KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm"
