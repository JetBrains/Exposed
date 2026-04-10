package org.jetbrains.exposed.v1.gradle.plugin

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

    /**
     * Package name where Exposed tables definitions are located.
     */
    @get:Input
    abstract val tablesPackage: Property<String>

    /**
     * Optional classpath that is scanned for Exposed table definitions.
     * Defaults to the project's runtime classpath.
     */
    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection

    /**
     * Directory where the generated migration scripts will be stored.
     * Defaults to `src/main/resources/db/migration`.
     */
    @get:OutputDirectory
    abstract val fileDirectory: DirectoryProperty

    /**
     * Optional prefix for migration script names. Defaults to "V".
     */
    @get:Input
    @get:Optional
    abstract val filePrefix: Property<String>

    /**
     * Optional separator for migration script names. Defaults to "__".
     */
    @get:Input
    @get:Optional
    abstract val fileSeparator: Property<String>

    /**
     * Optional flag for whether the descriptive part of migration script names should be all in upper-case.
     * Defaults to true.
     */
    @get:Input
    @get:Optional
    abstract val useUpperCaseDescription: Property<Boolean>

    /**
     * Optional file extension for migration scripts. Defaults to ".sql".
     */
    @get:Input
    @get:Optional
    abstract val fileExtension: Property<String>

    /**
     * Optional URL for the database connection, which should be considered as the current schema.
     */
    @get:Input
    @get:Optional
    abstract val databaseUrl: Property<String>

    /**
     * Optional username for the database connection, which should be considered as the current schema.
     */
    @get:Input
    @get:Optional
    abstract val databaseUser: Property<String>

    /**
     * Optional password for the database connection, which should be considered as the current schema.
     */
    @get:Input
    @get:Optional
    abstract val databasePassword: Property<String>

    /**
     * Optional Docker image name for when using TestContainers to apply existing scripts before generating new ones.
     */
    @get:Input
    @get:Optional
    abstract val testContainersImageName: Property<String>

    /**
     * The task's executor instance that accepts submitted work.
     */
    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    /**
     * Submits a [GenerateMigrationsWorker] work to be executed in isolated contexts.
     */
    @TaskAction
    fun generateMigrations() {
        workerExecutor
            .classLoaderIsolation()
            .submit(GenerateMigrationsWorker::class.java) { parameters ->
                parameters.tablesPackage = tablesPackage.get()
                parameters.classpathUrls = classpath.files.map { it.toURI().toURL() }

                parameters.fileDirectory.set(fileDirectory)
                parameters.filePrefix = filePrefix.get()
                parameters.fileSeparator = fileSeparator.get()
                parameters.useUpperCaseDescription = useUpperCaseDescription.get()
                parameters.fileExtension = fileExtension.get()

                if (databaseUrl.isPresent) parameters.databaseUrl = databaseUrl.get()
                if (databaseUser.isPresent) parameters.databaseUser = databaseUser.get()
                if (databasePassword.isPresent) parameters.databasePassword = databasePassword.get()
                if (testContainersImageName.isPresent) {
                    parameters.testContainersImageName = testContainersImageName.get()
                }

                parameters.debug = logger.isDebugEnabled
            }
    }
}
