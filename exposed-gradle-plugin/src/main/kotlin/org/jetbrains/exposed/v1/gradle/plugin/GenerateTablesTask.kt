package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.workers.WorkerExecutor
import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.UuidColumnType
import javax.inject.Inject

/**
 * Task for generating Exposed table definitions from an existing database schema.
 * Uses the Gradle Workers API for better isolation and parallel execution.
 */
abstract class GenerateTablesTask : DefaultTask() {

    /**
     * Package name where Exposed `Table` definitions are expected to be generated.
     */
    @get:Input
    @get:Optional
    abstract val tablesPackage: Property<String>

    /**
     * Classpath scanned for an Exposed date-time module when [dateTimeColumnType] is not set.
     * Defaults to the project's runtime classpath.
     */
    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection

    /**
     * Directory where generated table definitions are written.
     * Defaults to `src/main/kotlin`.
     */
    @get:OutputDirectory
    abstract val fileDirectory: DirectoryProperty

    /**
     * Names or glob patterns (`*` matches any sequence of characters, `?` a single character) of the database schemas
     * in which tables are searched for.
     * Defaults to an empty list, which searches only the connection's current schema.
     */
    @get:Input
    abstract val schemas: ListProperty<String>

    /**
     * Optional suffix appended to the name of every generated table object (and therefore to its file name).
     * Defaults to an empty string.
     */
    @get:Input
    @get:Optional
    abstract val objectNameSuffix: Property<String>

    /**
     * Which Exposed column function a database `UUID` column should be mapped to.
     * Defaults to `UuidColumnType.KOTLIN`.
     */
    @get:Input
    @get:Optional
    abstract val uuidColumnType: Property<UuidColumnType>

    /**
     * Which Exposed date-time module the functions for date-time columns are sourced from.
     * When not set, the module is detected on [classpath].
     * When none is found there, `KOTLIN_DATETIME` is used.
     */
    @get:Input
    @get:Optional
    abstract val dateTimeColumnType: Property<DateTimeColumnType>

    /**
     * Whether tables with a primary key should be generated as `IdTable` subclasses instead of plain `Table` objects.
     * Defaults to `false`.
     */
    @get:Input
    @get:Optional
    abstract val generateIdTables: Property<Boolean>

    /**
     * Exposed code to generate for particular columns in place of the generated mapping.
     * Defaults to an empty list.
     */
    @get:Input
    abstract val columnMappings: ListProperty<ColumnMapping>

    @get:Internal
    var fullFileName: String? = null
        private set

    /**
     * Optional command line argument that overrides the default of generating one file per table.
     * Passing an argument to this option means all table definitions will be stored in a single
     * generated file with the specified [name], with or without the `.kt` extension.
     */
    @Option(option = "filename", description = "The exact filename to use when generating a single file for all tables")
    fun setFullFileName(name: String) {
        this.fullFileName = name
    }

    /**
     * URL for the live database whose schema should be searched for tables to generate.
     */
    @get:Input
    @get:Optional
    abstract val databaseUrl: Property<String>

    /**
     * Username for the database connection.
     */
    @get:Input
    @get:Optional
    abstract val databaseUser: Property<String>

    /**
     * Password for the database connection.
     */
    @get:Input
    @get:Optional
    abstract val databasePassword: Property<String>

    /**
     * The task's executor instance that accepts submitted work.
     */
    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    /**
     * Submits a [GenerateTablesWorker] work to be executed in isolated contexts.
     */
    @TaskAction
    fun generateTables() {
        workerExecutor
            .classLoaderIsolation()
            .submit(GenerateTablesWorker::class.java) { parameters ->
                // properties with a default take it from the extension's conventions, which the plugin always wires up
                parameters.tablesPackage = tablesPackage.getOrElse("")
                parameters.fileDirectory.set(fileDirectory)
                parameters.fullFileName = fullFileName
                parameters.schemas = schemas.get()
                parameters.objectNameSuffix = objectNameSuffix.get()
                parameters.uuidColumnType = uuidColumnType.get()
                parameters.dateTimeColumnType = dateTimeColumnType.orNull
                parameters.classpathUrls = classpath.files.map { it.toURI().toURL() }
                parameters.generateIdTables = generateIdTables.get()
                parameters.columnMappings = columnMappings.get()

                if (databaseUrl.isPresent) parameters.databaseUrl = databaseUrl.get()
                if (databaseUser.isPresent) parameters.databaseUser = databaseUser.get()
                if (databasePassword.isPresent) parameters.databasePassword = databasePassword.get()

                parameters.debug = logger.isDebugEnabled
            }
    }
}

internal const val GENERATE_TABLES_TASK_NAME = "generateTables"
internal const val GENERATE_TABLES_TASK_DESCRIPTION = "Generates Exposed table definitions from an existing database schema"
