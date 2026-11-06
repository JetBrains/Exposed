package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.workers.WorkAction
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.plugin.core.tables.TableConfig
import org.jetbrains.exposed.v1.plugin.core.tables.TableGenerator

/**
 * Represents the implementation of a unit of work to be used when submitting work to the tables extension work executor.
 */
@OptIn(InternalApi::class)
abstract class GenerateTablesWorker : WorkAction<GenerateTablesParameters> {
    private val logger: Logger = Logging.getLogger(GenerateTablesWorker::class.java)

    override fun execute() {
        val params = parameters

        val tableGenerator = TableGenerator(
            config = params.toTableConfig(),
            logger = GradleMigrationLogger(logger, isDebugEnabled = params.debug),
        )
        tableGenerator.generate()
    }

    private fun GenerateTablesParameters.toTableConfig(): TableConfig {
        val fileDirectory = requireNotNull(fileDirectory.asFile.orNull) {
            "File directory must be set"
        }
        return TableConfig(
            tablesPackage = tablesPackage,
            classpathUrls = classpathUrls,
            fileDirectory = fileDirectory,
            fullFileName = fullFileName,
            schemas = schemas,
            objectNameSuffix = objectNameSuffix,
            uuidColumnType = uuidColumnType,
            dateTimeColumnType = dateTimeColumnType,
            generateIdTables = generateIdTables,
            columnMappings = columnMappings,
            databaseUrl = databaseUrl,
            databaseUser = databaseUser,
            databasePassword = databasePassword,
            debug = debug,
        )
    }
}
