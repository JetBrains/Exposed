package org.jetbrains.exposed.v1.migration.plugin.core

import java.io.File
import java.net.URL

/**
 * Build-tool-agnostic configuration for generating Exposed migration scripts.
 *
 * Mirrors the inputs accepted by the Gradle plugin's `GenerateMigrationsParameters` and the
 * Maven plugin's mojo parameters. Plain JVM types only — no Gradle `Property`, `DirectoryProperty`,
 * or `WorkParameters` leakage.
 *
 * Either ([databaseUrl], [databaseUser], [databasePassword]) **or** [testContainersImageName]
 * must be supplied. When both are absent, [MigrationGenerator] will fail.
 */
data class MigrationConfig(
    /**
     * Package name where Exposed `Table` definitions are expected to be located.
     */
    val tablesPackage: String,

    /**
     * Classpath URLs scanned for Exposed `Table` definitions.
     * Typically the consuming project's runtime classpath, plus the user's compiled `classes` dir.
     */
    val classpathUrls: List<URL>,

    /**
     * Directory where generated migration scripts are written.
     * Created if missing. Defaults to `src/main/resources/db/migration` at the plugin layer,
     * but no default is applied here.
     */
    val fileDirectory: File,

    /**
     * Prefix for migration script filenames. Defaults to `V` (Flyway convention).
     */
    val filePrefix: String = "V",

    /**
     * Version sub-pattern format for migration script filenames.
     * Defaults to `TIMESTAMP_ONLY` (`YYYYMMDDHHMMSS`).
     */
    val fileVersionFormat: VersionFormat = VersionFormat.TIMESTAMP_ONLY,

    /**
     * Separator between version and description in migration filenames. Defaults to `__`.
     */
    val fileSeparator: String = "__",

    /**
     * Whether the descriptive portion of a migration filename should be upper-cased.
     */
    val useUpperCaseDescription: Boolean = true,

    /**
     * File extension for migration scripts. Defaults to `.sql`.
     */
    val fileExtension: String = ".sql",

    /**
     * When non-null, overrides all other filename configuration: every generated statement is
     * written to a single file with this exact name (must include the extension).
     * The value is validated to prevent path traversal outside [fileDirectory].
     */
    val fullFileName: String? = null,

    /**
     * JDBC URL for the live database treated as the current schema.
     * Required when [testContainersImageName] is not provided.
     */
    val databaseUrl: String? = null,

    /**
     * Username for the database connection. Required when [testContainersImageName] is not provided.
     */
    val databaseUser: String? = null,

    /**
     * Password for the database connection. Required when [testContainersImageName] is not provided.
     */
    val databasePassword: String? = null,

    /**
     * Docker image name. When set, a Testcontainer is started and existing scripts in
     * [fileDirectory] are applied via Flyway before generating new ones.
     * Mutually exclusive with the live-database connection parameters.
     */
    val testContainersImageName: String? = null,

    /**
     * Whether DEBUG-level SQL logging should be emitted by the generator.
     */
    val debug: Boolean = false,
)
