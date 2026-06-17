package org.jetbrains.exposed.v1.maven.plugin

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import org.jetbrains.exposed.v1.migration.plugin.core.MigrationConfig
import org.jetbrains.exposed.v1.migration.plugin.core.MigrationGenerator
import org.jetbrains.exposed.v1.migration.plugin.core.MigrationLogger
import org.jetbrains.exposed.v1.migration.plugin.core.VersionFormat
import java.io.File
import java.net.URL

/**
 * Mojo for generating SQL migration scripts from Exposed table definitions.
 *
 * Goal: `exposed:generate-migration`.
 */
@Mojo(
    name = "generate-migration",
    requiresDependencyResolution = ResolutionScope.RUNTIME,
    threadSafe = true,
)
class GenerateMigrationMojo : AbstractMojo() {

    /**
     * The Maven project. Used to resolve the runtime classpath that is scanned
     * for Exposed table definitions.
     */
    @field:Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    /**
     * Package name where Exposed table definitions are located.
     */
    @field:Parameter(property = "exposed.tablesPackage", required = true)
    lateinit var tablesPackage: String

    /**
     * Directory where generated migration scripts will be stored.
     * Defaults to `src/main/resources/db/migration` relative to the project root.
     */
    @field:Parameter(
        property = "exposed.fileDirectory",
        defaultValue = "\${project.basedir}/src/main/resources/db/migration",
    )
    lateinit var fileDirectory: File

    /**
     * Prefix for migration script names. Defaults to `V`.
     */
    @field:Parameter(property = "exposed.filePrefix", defaultValue = "V")
    var filePrefix: String = "V"

    /**
     * Version format for migration script names.
     * Defaults to using the full current timestamp (with seconds) in the format YYYYMMDDHHMMSS.
     */
    @field:Parameter(property = "exposed.fileVersionFormat", defaultValue = "TIMESTAMP_ONLY")
    var fileVersionFormat: VersionFormat = VersionFormat.TIMESTAMP_ONLY

    /**
     * Separator for migration script names. Defaults to `__`.
     */
    @field:Parameter(property = "exposed.fileSeparator", defaultValue = "__")
    var fileSeparator: String = "__"

    /**
     * Whether the descriptive part of migration script names should be all upper-case.
     * Defaults to `true`.
     */
    @field:Parameter(property = "exposed.useUpperCaseDescription", defaultValue = "true")
    var useUpperCaseDescription: Boolean = true

    /**
     * File extension for migration scripts. Defaults to `.sql`.
     */
    @field:Parameter(property = "exposed.fileExtension", defaultValue = ".sql")
    var fileExtension: String = ".sql"

    /**
     * Override that writes all migration statements to a single file with the given name.
     * Must include the file extension. When set, all other filename configuration is ignored.
     */
    @field:Parameter(property = "exposed.filename")
    var fullFileName: String? = null

    /**
     * JDBC URL for the live database treated as the current schema.
     * Required when [testContainersImageName] is not set.
     */
    @field:Parameter(property = "exposed.databaseUrl")
    var databaseUrl: String? = null

    /**
     * Username for the database connection.
     * Required when [testContainersImageName] is not set.
     */
    @field:Parameter(property = "exposed.databaseUser")
    var databaseUser: String? = null

    /**
     * Password for the database connection.
     * Required when [testContainersImageName] is not set.
     */
    @field:Parameter(property = "exposed.databasePassword")
    var databasePassword: String? = null

    /**
     * Docker image name. When set, a Testcontainer is started and existing scripts in
     * [fileDirectory] are applied via Flyway before generating new ones.
     * Mutually exclusive with the live-database connection parameters.
     */
    @field:Parameter(property = "exposed.testContainersImageName")
    var testContainersImageName: String? = null

    /**
     * Whether to enable debug logging.
     */
    @field:Parameter(property = "exposed.debug", defaultValue = "false")
    var debug: Boolean = false

    private val classpathUrls: List<URL>
        get() = project.runtimeClasspathElements
            .map { File(it).toURI().toURL() }

    override fun execute() {
        val migrationGenerator = MigrationGenerator(
            config = migrationConfig,
            logger = object : MigrationLogger {
                override fun lifecycle(message: String) {
                    getLog().info(message)
                }

                override fun debug(message: String) {
                    if (!isDebugEnabled) return
                    getLog().debug(message)
                }

                override val isDebugEnabled: Boolean = debug
            }
        )
        migrationGenerator.generate()
    }

    private val migrationConfig: MigrationConfig
        get() = MigrationConfig(
            tablesPackage = tablesPackage,
            classpathUrls = classpathUrls,
            fileDirectory = fileDirectory,
            filePrefix = filePrefix,
            fileVersionFormat = fileVersionFormat,
            fileSeparator = fileSeparator,
            useUpperCaseDescription = useUpperCaseDescription,
            fileExtension = fileExtension,
            fullFileName = fullFileName,
            databaseUrl = databaseUrl,
            databaseUser = databaseUser,
            databasePassword = databasePassword,
            testContainersImageName = testContainersImageName,
            debug = debug,
        )
}
