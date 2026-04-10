package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

/**
 * A plugin extension of the Gradle build tool that applies configured automations to specific Exposed functionality.
 */
class ExposedGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions
            .create(MigrationsExtension.NAME, MigrationsExtension::class.java, project.objects)

        // Set default migration directory
        extension.fileDirectory.convention(project.layout.projectDirectory.dir("src/main/resources/db/migration"))

        // Set default classpath for Exposed tables
        val classpath = project
            .extensions
            .findByType(SourceSetContainer::class.java)
            ?.findByName("main")
            ?.runtimeClasspath

        if (classpath != null) extension.classpath.setFrom(classpath)

        project.tasks.register(GENERATE_MIGRATIONS_TASK_NAME, GenerateMigrationsTask::class.java) {
            it.description = GENERATE_MIGRATIONS_TASK_DESCRIPTION
            it.group = TASK_GROUP

            it.tablesPackage.set(extension.tablesPackage)
            it.classpath.setFrom(extension.classpath.toList())

            it.fileDirectory.set(extension.fileDirectory)
            it.filePrefix.set(extension.filePrefix)
            it.fileSeparator.set(extension.fileSeparator)
            it.useUpperCaseDescription.set(extension.useUpperCaseDescription)
            it.fileExtension.set(extension.fileExtension)

            it.databaseUrl.set(extension.databaseUrl)
            it.databaseUser.set(extension.databaseUser)
            it.databasePassword.set(extension.databasePassword)

            it.testContainersImageName.set(extension.testContainersImageName)
        }
    }

    companion object {
        /** The Exposed plugin version, which should be equal to the Exposed version used in a project. */
        const val VERSION: String = "1.2.0"

        /** The group name used for Exposed tasks. */
        const val TASK_GROUP: String = "Exposed"
    }
}
