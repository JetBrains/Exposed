package org.jetbrains.exposed.migration.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

class ExposedMigrationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension =
            project.extensions.create("exposedMigration", ExposedMigrationExtension::class.java, project.objects)

        // Set default Migration Dir
        extension.migrationsDir.convention(project.layout.projectDirectory.dir("src/main/resources/db/migration"))

        val classpath = project
            .extensions
            .findByType(SourceSetContainer::class.java)
            ?.findByName("main")
            ?.runtimeClasspath

        if (classpath != null) extension.classpath.setFrom(classpath)

        project.tasks.register("generateMigrations", GenerateMigrationsTask::class.java) {
            it.description = "Generates SQL migration scripts from Exposed table definitions"
            it.group = "exposed"

            it.migrationsDir.set(extension.migrationsDir)
            it.exposedTablesPackage.set(extension.exposedTablesPackage)
            it.migrationFilePrefix.set(extension.migrationFilePrefix)
            it.migrationFileSeparator.set(extension.migrationFileSeparator)
            it.migrationFileExtension.set(extension.migrationFileExtension)

            // Database connection properties (optional if TestContainers is used)
            it.databaseUrl.set(extension.databaseUrl)
            it.databaseUser.set(extension.databaseUser)
            it.databasePassword.set(extension.databasePassword)

            it.testContainersImageName.set(extension.testContainersImageName)
            it.classpath.setFrom(extension.classpath.toList())
        }
    }
}
