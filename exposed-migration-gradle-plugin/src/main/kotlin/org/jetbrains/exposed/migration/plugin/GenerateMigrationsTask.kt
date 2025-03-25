package org.jetbrains.exposed.migration.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Task for generating SQL migration scripts from Exposed table definitions.
 * Uses the Gradle Workers API for better isolation and parallel execution.
 */
abstract class GenerateMigrationsTask : DefaultTask() {

    @get:OutputDirectory
    abstract val migrationsDir: DirectoryProperty

    @get:Input
    abstract val exposedTablesPackage: Property<String>

    @get:Input
    @get:Optional
    abstract val migrationFilePrefix: Property<String>

    @get:Input
    @get:Optional
    abstract val migrationFileSeparator: Property<String>

    @get:Input
    @get:Optional
    abstract val migrationFileExtension: Property<String>

    @get:Input
    @get:Optional
    abstract val databaseUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val databaseUser: Property<String>

    @get:Input
    @get:Optional
    abstract val databasePassword: Property<String>

    @get:Input
    @get:Optional
    abstract val testContainersImageName: Property<String>

    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun generateMigrations() {
        workerExecutor
            .classLoaderIsolation()
            .submit(GenerateMigrationsWorker::class.java) { parameters ->
                parameters.migrationsDir.set(migrationsDir)
                parameters.exposedTablesPackage = exposedTablesPackage.get()
                parameters.migrationFilePrefix = migrationFilePrefix.get()
                parameters.migrationFileSeparator = migrationFileSeparator.get()
                parameters.migrationFileExtension = migrationFileExtension.get()

                if (databaseUrl.isPresent) parameters.databaseUrl = databaseUrl.get()
                if (databaseUser.isPresent) parameters.databaseUser = databaseUser.get()
                if (databasePassword.isPresent) parameters.databasePassword = databasePassword.get()
                if (testContainersImageName.isPresent) {
                    parameters.testContainersImageName = testContainersImageName.get()
                }

                parameters.classpathUrls = classpath.files.map { it.toURI().toURL() }

                parameters.debug = logger.isDebugEnabled
            }
    }
}
