package org.jetbrains.exposed.v1.migration.plugin.core

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.expandArgs
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.OracleContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mssqlserver.MSSQLServerContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.net.URLClassLoader
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.time.Clock

/**
 * Build-tool-agnostic core of the Exposed migration plugin.
 *
 * Given a [MigrationConfig] and a [MigrationLogger], scans the configured classpath for Exposed
 * `Table` definitions, compares them against a live (or container-backed) database, and writes
 * one or more SQL migration scripts to [MigrationConfig.fileDirectory].
 *
 * Consumed by:
 *  - `exposed-gradle-plugin`'s `GenerateMigrationsWorker`
 *  - `exposed-maven-plugin`'s `GenerateMigrationMojo`
 *
 * Pure JVM; no Gradle or Maven types appear on its API surface.
 */
class MigrationGenerator(
    private val config: MigrationConfig,
    private val logger: MigrationLogger,
) {

    /**
     * Runs the generator end-to-end.
     *
     * @return list of filenames (relative to [MigrationConfig.fileDirectory]) that were written.
     * @throws IOException if [MigrationConfig.fullFileName] resolves to a path outside [MigrationConfig.fileDirectory].
     * @throws IllegalArgumentException if neither a live database nor a Testcontainer image is configured.
     */
    fun generate(): List<String> {
        val migrationsDirectory = config.fileDirectory
        if (!migrationsDirectory.exists()) {
            migrationsDirectory.mkdirs()
        }
        val expectedFileName = config.fullFileName
        // throws IOException if user-defined filename would write outside the provided migrations directory
        val migrationFile = expectedFileName?.validateFilePath(migrationsDirectory)

        val generated: List<String> = if (expectedFileName != null && migrationFile != null) {
            generateSingleFile(expectedFileName, migrationFile)
        } else {
            generateVersionedFiles(migrationsDirectory)
        }

        logger.lifecycle("")
        logger.lifecycle("# Exposed Migrations Generated ${generated.size} migrations:")
        generated.forEach { logger.lifecycle("  * $it") }
        logger.lifecycle("")

        return generated
    }

    private fun generateSingleFile(expectedFileName: String, migrationFile: File): List<String> =
        withClassloader { classloader ->
            withDatabase { database ->
                val tables = classloader
                    .getClassesInPackage(config.tablesPackage)
                    .mapNotNull { it.tableOrNull() }
                    .toList()
                    .toTypedArray()
                transaction(database) {
                    addLogger(GeneratorSqlLogger())
                    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                        tables = tables,
                        withLogs = config.debug,
                    )
                    migrationFile.writeText(statements.joinToString(";\n", postfix = ";"))
                    listOf(expectedFileName)
                }
            }
        }

    private fun generateVersionedFiles(migrationsDirectory: File): List<String> {
        val versionGenerator = config.fileVersionFormat.nextVersion(
            migrationsDirectory,
            Clock.System,
            config.filePrefix,
            config.fileSeparator,
        )

        return withClassloader { classloader ->
            withDatabase { database ->
                var ignored = 0
                val foundTables = classloader
                    .getClassesInPackage(config.tablesPackage)
                    .mapNotNull { it.tableOrNull() }
                val sortedTables = SchemaUtils.sortTablesByReferences(foundTables.toList())
                sortedTables.mapIndexedNotNull { index, table ->
                    transaction(database) {
                        addLogger(GeneratorSqlLogger())
                        val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                            table,
                            withLogs = config.debug,
                        )
                        if (statements.isNotEmpty()) {
                            val description = statements.first()
                                .statementToFileDescription(config.useUpperCaseDescription)
                            val version = versionGenerator(index - ignored)
                            val fileName = "$version$description${config.fileExtension}"
                            val file = File(migrationsDirectory, fileName)
                            file.writeText(statements.joinToString(";\n", postfix = ";"))
                            fileName
                        } else {
                            ignored++
                            null
                        }
                    }
                }.distinct()
            }
        }
    }

    private inline fun <A> withDatabase(block: (Database) -> A): A {
        val imageName = config.testContainersImageName
        return if (imageName != null) {
            container(imageName).use { container ->
                withDatabase(container.jdbcUrl, container.username, container.password) { database ->
                    val migrationsDirectory = config.fileDirectory
                    if (migrationsDirectory.walk().any()) {
                        Flyway.configure()
                            .dataSource(container.jdbcUrl, container.username, container.password)
                            .locations("filesystem:${migrationsDirectory.absolutePath}")
                            .load()
                            .migrate()
                    }
                    block(database)
                }
            }
        } else {
            require(
                config.databaseUrl != null &&
                    config.databaseUser != null &&
                    config.databasePassword != null
            ) {
                "Database properties (url, user, password) must be provided when not using TestContainers"
            }
            withDatabase(config.databaseUrl, config.databaseUser, config.databasePassword, block)
        }
    }

    private fun container(imageName: String): JdbcDatabaseContainer<*> = when {
        SupportedImage.POSTGRES.prefixMatches(imageName) -> PostgreSQLContainer(imageName)
        SupportedImage.MYSQL.prefixMatches(imageName) -> MySQLContainer(imageName)
        SupportedImage.MARIADB.prefixMatches(imageName) -> MariaDBContainer(imageName)
        SupportedImage.ORACLE.prefixMatches(imageName) -> OracleContainer(imageName)
        SupportedImage.SQLSERVER.prefixMatches(imageName) -> MSSQLServerContainer(imageName)

        else -> throw IllegalArgumentException(
            "Unsupported database container image: $imageName. ${SupportedImage.supportedPrefixesMessage}"
        )
    }.apply {
        waitingFor(Wait.forListeningPort())
        start()
    }

    private inline fun <A> withDatabase(url: String, user: String, password: String, block: (Database) -> A): A {
        val db = Database.connect(url = url, user = user, password = password)
        return try {
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    private fun KClass<*>.tableOrNull(): Table? = if (isSubclassOf(Table::class) && !isAbstract) {
        (objectInstance as? Table)
    } else {
        null
    }

    private inline fun <A> withClassloader(block: (URLClassLoader) -> A): A {
        val original = Thread.currentThread().contextClassLoader
        return try {
            val urls = config.classpathUrls.toTypedArray()
            val classLoader = URLClassLoader(urls, original)
            Thread.currentThread().contextClassLoader = classLoader
            block(classLoader)
        } finally {
            Thread.currentThread().contextClassLoader = original
        }
    }

    private fun URLClassLoader.getClassesInPackage(packageName: String): Sequence<KClass<*>> = getResources(
        packageName.replace('.', '/')
    )
        .asSequence()
        .flatMap { resource ->
            File(resource.toURI())
                .walk()
                .filter { file -> file.isFile && file.name.endsWith(CLASS_EXTENSION) }
                .map { file ->
                    val baseDir = File(resource.toURI())
                    val subPackageName = file.relativeTo(baseDir)
                        .path
                        .replace(separator, ".")
                        .dropLast(file.name.length + 1)
                    val fullPackage = "$packageName.${if (subPackageName.isBlank()) "" else "$subPackageName."}"
                    val clazzName = file.name.dropLast(CLASS_EXTENSION.length)
                    Class.forName("$fullPackage$clazzName", true, this).kotlin
                }
        }

    private fun String.validateFilePath(parentPath: File): File {
        val baseDir = parentPath.canonicalFile
        val requestedFile = File(baseDir, this)

        val canonicalPath = requestedFile.canonicalPath
        if (!canonicalPath.startsWith(baseDir.path)) {
            throw IOException("Provided fileName is on a different path than provided migrations directory: $parentPath")
        }

        return requestedFile
    }

    private inner class GeneratorSqlLogger : SqlLogger {
        override fun log(context: StatementContext, transaction: Transaction) {
            if (config.debug) {
                logger.debug(context.expandArgs(transaction))
            }
        }
    }

    private enum class SupportedImage(vararg val prefixes: String) {
        MYSQL("mysql"),
        MARIADB("mariadb"),
        POSTGRES("postgres"),
        SQLSERVER("mcr.microsoft.com/mssql/server"),
        ORACLE("container-registry.oracle.com/", "gvenzl/oracle-", "oracle/");

        fun prefixMatches(name: String): Boolean = prefixes.any { prefix -> name.startsWith(prefix) }

        companion object {
            val supportedPrefixesMessage: String
                get() = "Supported prefixes are: ${entries.joinToString { si -> si.prefixes.joinToString { it } }}"
        }
    }

    private companion object {
        const val CLASS_EXTENSION = ".class"
    }
}
