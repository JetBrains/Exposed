package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.file.DirectoryProperty
import org.gradle.workers.WorkParameters
import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.UuidColumnType
import java.net.URL

/**
 * Parameter objects for the table generation extension's work actions.
 */
interface GenerateTablesParameters : WorkParameters {
    /**
     * Package name where Exposed `Table` definitions are expected to be generated.
     */
    var tablesPackage: String

    /**
     * Classpath scanned for an Exposed date-time module when [dateTimeColumnType] is not set.
     */
    var classpathUrls: List<URL>

    /**
     * Directory where generated table definitions are written.
     * Defaults to `src/main/kotlin`.
     */
    val fileDirectory: DirectoryProperty

    /**
     * Optional command line argument that overrides the default of generating one file per table.
     * Passing an argument to this option means all table definitions will be stored in a single
     * generated file of this name, with or without the `.kt` extension.
     */
    var fullFileName: String?

    /**
     * Names or glob patterns (`*` matches any sequence of characters, `?` a single character) of the database schemas
     * in which tables are searched for.
     * Defaults to an empty list, which searches only the connection's current schema.
     */
    var schemas: List<String>

    /**
     * Optional suffix appended to the name of every generated table object (and therefore to its file name).
     * Defaults to an empty string.
     */
    var objectNameSuffix: String

    /**
     * Which Exposed column function a database `UUID` column should be mapped to.
     * Defaults to `UuidColumnType.KOTLIN`.
     */
    var uuidColumnType: UuidColumnType

    /**
     * Which Exposed date-time module the functions for date-time columns are sourced. from.
     * When `null` the module is detected from [classpathUrls].
     * When none is found there, `KOTLIN_DATETIME` is used.
     */
    var dateTimeColumnType: DateTimeColumnType?

    /**
     * Whether tables with a primary key should be generated as `IdTable` subclasses instead of plain `Table` objects.
     */
    var generateIdTables: Boolean

    /**
     * Exposed code to generate for particular columns in place of the generated mapping.
     */
    var columnMappings: List<ColumnMapping>

    /**
     * URL for the live database whose schema should be searched for tables to generate.
     */
    var databaseUrl: String?

    /**
     * Username for the database connection.
     */
    var databaseUser: String?

    /**
     * Password for the database connection.
     */
    var databasePassword: String?

    /**
     * Whether logger DEBUG level is enabled.
     */
    var debug: Boolean
}
