package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.jetbrains.exposed.v1.gradle.plugin.ExposedGradlePlugin.Companion.TASK_GROUP
import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.UuidColumnType
import javax.inject.Inject

/**
 * Configuration of the tables generation extension for the Exposed Gradle Plugin.
 *
 * This extension allows users to configure the generation of Exposed table definitions from an existing database
 * in their `build.gradle.kts` file in a nested `tables` block.
 */
open class TablesExtension @Inject internal constructor(objects: ObjectFactory) {

    /**
     * Package name where Exposed `Table` definitions are expected to be generated.
     */
    val tablesPackage: Property<String> = objects.property(String::class.java)

    /**
     * Classpath scanned for an Exposed date-time module when [dateTimeColumnType] is not set.
     * Defaults to the project's runtime classpath.
     */
    val classpath: ConfigurableFileCollection = objects.fileCollection()

    /**
     * Directory where generated table definitions are written.
     * Defaults to `src/main/kotlin`.
     */
    val fileDirectory: DirectoryProperty = objects.directoryProperty()

    /**
     * Names or glob patterns (`*` matches any sequence of characters, `?` a single character) of the database schemas
     * in which tables are searched for.
     * Defaults to an empty list, which searches only the connection's current schema.
     */
    val schemas: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

    /**
     * Suffix appended to the name of every generated table object (and therefore to its file name).
     * Defaults to an empty string.
     */
    val objectNameSuffix: Property<String> = objects.property(String::class.java).convention("")

    /**
     * Which Exposed column function a database `UUID` column should be mapped to.
     * Defaults to `UuidColumnType.KOTLIN`, which generates `uuid()` columns of `kotlin.uuid.Uuid`.
     */
    val uuidColumnType: Property<UuidColumnType> = objects.property(UuidColumnType::class.java)
        .convention(UuidColumnType.KOTLIN)

    /**
     * Which Exposed date-time module the functions for date-time columns are sourced from.
     * When `null`, the module is detected on [classpath].
     * When none is found there, [DateTimeColumnType.KOTLIN_DATETIME] is used.
     */
    val dateTimeColumnType: Property<DateTimeColumnType> = objects.property(DateTimeColumnType::class.java)

    /**
     * Whether tables with a primary key should be generated as `IdTable` subclasses instead of plain `Table` objects.
     * Defaults to `false`.
     */
    val generateIdTables: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /**
     * Exact Exposed code to generate for particular columns in place of the generated mapping.
     * Each mapping must match exactly one column of the generated tables, otherwise generation fails.
     * Defaults to an empty list.
     */
    val columnMappings: ListProperty<ColumnMapping> = objects.listProperty(ColumnMapping::class.java).convention(emptyList())

    /**
     * Configures exact Exposed code to generate for particular columns, for example:
     * ```
     * columnMappings {
     *     column("EVENTS.CREATED_AT") { code = "timestamp(\"CREATED_AT\")" }
     *     column("EVENTS.PAYLOAD") {
     *         code = "json<Payload>(\"PAYLOAD\", Json.Default)"
     *         imports = listOf("com.example.Payload", "kotlinx.serialization.json.Json")
     *     }
     * }
     * ```
     */
    fun columnMappings(action: Action<ColumnMappingsSpec>) {
        action.execute(ColumnMappingsSpec(columnMappings))
    }

    /**
     * URL for the live database whose schema should be searched for tables to generate.
     */
    val databaseUrl: Property<String> = objects.property(String::class.java)

    /**
     * Username for the database connection.
     */
    val databaseUser: Property<String> = objects.property(String::class.java)

    /**
     * Password for the database connection.
     */
    val databasePassword: Property<String> = objects.property(String::class.java)

    companion object {
        const val NAME: String = "tables"
    }
}

/**
 * The `columnMappings { }` block of the tables extension, which adds one [ColumnMapping] per [column] call.
 */
class ColumnMappingsSpec internal constructor(private val mappings: ListProperty<ColumnMapping>) {
    /**
     * Maps the column [key] to the code configured in [action], which must set [ColumnMappingSpec.code].
     */
    fun column(key: String, action: Action<ColumnMappingSpec>) {
        val spec = ColumnMappingSpec()
        action.execute(spec)
        val code = requireNotNull(spec.code) { "The column mapping for '$key' must set code" }
        mappings.add(ColumnMapping(key, code, spec.imports))
    }
}

/**
 * The code to generate for one mapped column, see [ColumnMapping].
 */
class ColumnMappingSpec internal constructor() {
    /** The column factory call, including the column name and without modifiers. */
    var code: String? = null

    /**
     * Fully qualified names that [code] needs imported. The import of an Exposed extension function the generator
     * itself knows is inferred when this is empty.
     */
    var imports: List<String> = emptyList()
}

internal fun Project.configureTables() {
    val extension = createExposedExtension<TablesExtension>(TablesExtension.NAME)

    // Set default source directory for generated table definitions
    extension.fileDirectory.convention(
        layout.projectDirectory.dir("src/main/kotlin")
    )

    // Set default classpath for detecting the Exposed date/time module
    mainRuntimeClasspath?.let { extension.classpath.setFrom(it) }

    tasks.register(GENERATE_TABLES_TASK_NAME, GenerateTablesTask::class.java) {
        it.description = GENERATE_TABLES_TASK_DESCRIPTION
        it.group = TASK_GROUP

        // forces the task to be out-of-date, ensuring it executes every time the build is run.
        // The real input is the database schema, which cannot be tracked by Gradle
        it.outputs.upToDateWhen { false }

        it.tablesPackage.set(extension.tablesPackage)
        it.fileDirectory.set(extension.fileDirectory)
        it.schemas.set(extension.schemas)
        it.objectNameSuffix.set(extension.objectNameSuffix)
        it.uuidColumnType.set(extension.uuidColumnType)
        it.dateTimeColumnType.set(extension.dateTimeColumnType)
        it.classpath.setFrom(extension.classpath)
        it.generateIdTables.set(extension.generateIdTables)
        it.columnMappings.set(extension.columnMappings)

        it.databaseUrl.set(extension.databaseUrl)
        it.databaseUser.set(extension.databaseUser)
        it.databasePassword.set(extension.databasePassword)
    }
}
