package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.file.DirectoryProperty
import org.gradle.workers.WorkParameters
import java.net.URL

/**
 * Parameter objects for the migrations extension's work actions.
 */
interface GenerateMigrationsParameters : WorkParameters {
    /**
     * Package name where Exposed table definitions are expected to be located.
     */
    var tablesPackage: String

    /**
     * Optional classpath that is scanned for Exposed table definitions.
     * Defaults to the project's runtime classpath.
     */
    var classpathUrls: List<URL>

    /**
     * Directory where the generated migration scripts will be stored.
     * Defaults to `src/main/resources/db/migration`.
     */
    val fileDirectory: DirectoryProperty

    /**
     * Optional prefix for migration script names.
     * Defaults to `V`.
     */
    var filePrefix: String

    /**
     * Optional version format for migration script names.
     * Defaults to using the full current timestamp (with seconds) in the format YYYYMMDDHHMMSS.
     */
    var fileVersionFormat: VersionFormat

    /**
     * Optional separator for migration script names.
     * Defaults to `__`.
     */
    var fileSeparator: String

    /**
     * Optional flag for whether the descriptive part of migration script names should be all in upper-case.
     * Defaults to `true`.
     */
    var useUpperCaseDescription: Boolean

    /**
     * Optional file extension for migration scripts.
     * Defaults to `.sql`.
     */
    var fileExtension: String

    /**
     * Optional command line argument that overrides any filename configurations declared in the build file.
     * Passing an argument to this option means all migration statements will be stored in a single
     * generated file of this name, which should include the required extension as well.
     */
    var fullFileName: String?

    /**
     * URL for the database connection, which should be considered as the current schema.
     * This is optional only if `testContainersImageName` is not set.
     */
    var databaseUrl: String?

    /**
     * Username for the database connection, which should be considered as the current schema.
     * This is optional only if `testContainersImageName` is not set.
     */
    var databaseUser: String?

    /**
     * Password for the database connection, which should be considered as the current schema.
     * This is optional only if `testContainersImageName` is not set.
     */
    var databasePassword: String?

    /**
     * Docker image name for when using TestContainers to apply existing scripts before generating new ones.
     * This is optional only if no values are set for any database properties.
     */
    var testContainersImageName: String?

    /**
     * Whether logger DEBUG level is enabled.
     */
    var debug: Boolean
}
