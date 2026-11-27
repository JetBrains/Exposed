package org.jetbrains.exposed.v1.plugin.core.tables

import org.jetbrains.exposed.v1.core.InternalApi
import java.io.File
import java.net.URL

/**
 * Build-tool-agnostic configuration for generating Exposed `Table` definitions from an existing database schema.
 *
 * Mirrors the inputs accepted by the Gradle plugin's `GenerateTablesParameters`.
 * Plain JVM types only — no Gradle `Property`, `DirectoryProperty`, or `WorkParameters` leakage.
 *
 * All of ([databaseUrl], [databaseUser], [databasePassword]) must be supplied.
 * @suppress
 */
@InternalApi
data class TableConfig(
    /**
     * Package name where Exposed `Table` definitions are expected to be generated.
     */
    val tablesPackage: String,

    /**
     * Classpath URLs scanned for an Exposed date-time module when [dateTimeColumnType] is not set.
     * Typically the consuming project's runtime classpath.
     */
    val classpathUrls: List<URL> = emptyList(),

    /**
     * Directory where generated table definitions are written.
     * Created if missing. Defaults to `src/main/kotlin` at the plugin layer,
     * but no default is applied here.
     */
    val fileDirectory: File,

    /**
     * When non-null, every generated table object is written to a single file with this name
     * (with or without the `.kt` extension) instead of one file per table.
     */
    val fullFileName: String? = null,

    /**
     * Names or glob patterns (`*` matches any sequence of characters, `?` a single character) of the database schemas
     * in which tables are searched for. When empty, only the connection's current schema is searched.
     */
    val schemas: List<String> = emptyList(),

    /**
     * Suffix appended to the name of every generated table object (and therefore to its file name).
     * Defaults to an empty string.
     */
    val objectNameSuffix: String = "",

    /**
     * Which Exposed column function a database `UUID` column should be mapped to.
     * Defaults to [UuidColumnType.KOTLIN], which generates `uuid()` columns of [kotlin.uuid.Uuid].
     */
    val uuidColumnType: UuidColumnType = UuidColumnType.KOTLIN,

    /**
     * Which Exposed date-time module the functions for date-time columns are sourced from.
     * When `null`, the module is detected on [classpathUrls].
     * When none is found there, [DateTimeColumnType.KOTLIN_DATETIME] is used.
     */
    val dateTimeColumnType: DateTimeColumnType? = null,

    /**
     * Whether tables with a primary key should be generated as `IdTable` subclasses instead of plain `Table` objects.
     * Defaults to `false`.
     */
    val generateIdTables: Boolean = false,

    /**
     * Exact Exposed code to generate for particular columns in place of the generated mapping.
     * Each mapping must match exactly one column of the generated tables, otherwise generation fails.
     * Defaults to an empty list.
     */
    val columnMappings: List<ColumnMapping> = emptyList(),

    /**
     * JDBC URL for the live database whose schema should be searched for tables to generate.
     */
    val databaseUrl: String? = null,

    /**
     * Username for the database connection.
     */
    val databaseUser: String? = null,

    /**
     * Password for the database connection.
     */
    val databasePassword: String? = null,

    /**
     * Whether DEBUG-level logging should be emitted by the generator.
     */
    val debug: Boolean = false,
)
