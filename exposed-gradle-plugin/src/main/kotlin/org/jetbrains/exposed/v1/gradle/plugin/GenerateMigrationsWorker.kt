package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.workers.WorkAction
import org.jetbrains.exposed.v1.migration.plugin.core.MigrationConfig
import org.jetbrains.exposed.v1.migration.plugin.core.MigrationGenerator
import org.jetbrains.exposed.v1.migration.plugin.core.MigrationLogger

/**
 * Represents the implementation of a unit of work to be used when submitting work to the migrations extension work executor.
 */
abstract class GenerateMigrationsWorker : WorkAction<GenerateMigrationsParameters> {
    private val logger: Logger = Logging.getLogger(GenerateMigrationsWorker::class.java)

    override fun execute() {
        val params = parameters

        val migrationGenerator = MigrationGenerator(
            config = params.toMigrationConfig(),
            logger = object : MigrationLogger {
                override fun lifecycle(message: String) {
                    logger.lifecycle(message)
                }

                override fun debug(message: String) {
                    if (!isDebugEnabled) return
                    logger.debug(message)
                }

                override val isDebugEnabled: Boolean = params.debug
            },
        )
        migrationGenerator.generate()
    }
}

private fun GenerateMigrationsParameters.toMigrationConfig(): MigrationConfig {
    val fileDirectory = requireNotNull(fileDirectory.asFile.orNull) {
        "File directory must be set"
    }
    return MigrationConfig(
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
