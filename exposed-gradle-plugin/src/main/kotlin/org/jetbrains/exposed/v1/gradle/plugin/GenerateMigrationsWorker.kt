package org.jetbrains.exposed.v1.gradle.plugin

import org.flywaydb.core.Flyway
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.workers.WorkAction
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
import java.net.URLClassLoader
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.time.Clock

/**
 * Represents the implementation of a unit of work to be used when submitting work to the migrations extension work executor.
 */
abstract class GenerateMigrationsWorker : WorkAction<GenerateMigrationsParameters> {
    private val logger: Logger = Logging.getLogger(GenerateMigrationsWorker::class.java)
    private val classExtensionLength: Int = ".class".length

    override fun execute() {
        val params = parameters
        val migrationsDirectory = params.fileDirectory.get().asFile
        if (!migrationsDirectory.exists()) {
            migrationsDirectory.mkdirs()
        }
        val expectedFileName = params.fullFileName

        val generated: List<String> = if (expectedFileName != null) {
            withClassloader { classloader ->
                withDatabase { database ->
                    val tables = classloader
                        .getClassesInPackage(params.tablesPackage)
                        .mapNotNull { it.tableOrNull() }
                        .toList()
                        .toTypedArray()
                    transaction(database) {
                        addLogger(GradleLogger())
                        val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                            tables = tables,
                            withLogs = params.debug
                        )
                        val migrationFile = File(migrationsDirectory, expectedFileName)
                        migrationFile.writeText(statements.joinToString(";\n", postfix = ";"))
                        listOf(expectedFileName)
                    }
                }
            }
        } else {
            val prefix = params.filePrefix
            val separator = params.fileSeparator
            val extension = params.fileExtension
            val versionGenerator = params.fileVersionFormat.nextVersion(migrationsDirectory, Clock.System, prefix, separator)

            withClassloader { classloader ->
                withDatabase { database ->
                    var ignored = 0
                    val foundTables = classloader
                        .getClassesInPackage(params.tablesPackage)
                        .mapNotNull { it.tableOrNull() }
                    val sortedTables = SchemaUtils.sortTablesByReferences(foundTables.toList())
                    sortedTables.mapIndexedNotNull { index, table ->
                        transaction(database) {
                            addLogger(GradleLogger())
                            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                                table,
                                withLogs = params.debug
                            )
                            if (statements.isNotEmpty()) {
                                val description = statements.first().statementToFileDescription(params.useUpperCaseDescription)
                                val version = versionGenerator(index - ignored)
                                val fileName = "$version$description$extension"
                                val migrationFile = File(migrationsDirectory, fileName)
                                migrationFile.writeText(statements.joinToString(";\n", postfix = ";"))
                                fileName
                            } else {
                                ignored++
                                null
                            }
                        }
                    }.toList()
                }
            }
        }

        logger.lifecycle("")
        logger.lifecycle("# Exposed Migrations Generated ${generated.size} migrations:")
        generated.forEach { logger.lifecycle("  * $it") }
        logger.lifecycle("")
    }

    private inline fun <A> withDatabase(block: (Database) -> A): A = if (parameters.testContainersImageName != null) {
        container(parameters.testContainersImageName!!).use { container ->
            withDatabase(container.jdbcUrl, container.username, container.password) { database ->
                val migrationsDirectory = parameters.fileDirectory.get().asFile
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
            parameters.databaseUrl != null &&
                parameters.databaseUser != null &&
                parameters.databasePassword != null
        ) {
            "Database properties (url, user, password) must be provided when not using TestContainers"
        }
        withDatabase(parameters.databaseUrl!!, parameters.databaseUser!!, parameters.databasePassword!!, block)
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
        (objectInstance as Table)
    } else {
        null
    }

    private inline fun <A> withClassloader(block: (URLClassLoader) -> A): A {
        val original = Thread.currentThread().contextClassLoader
        return try {
            val urls = parameters.classpathUrls.toTypedArray()
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
                .filter { file -> file.isFile && file.name.endsWith(".class") }
                .map { file ->
                    val baseDir = File(resource.toURI())
                    val subPackageName = file.relativeTo(baseDir)
                        .path
                        .replace(separator, ".")
                        .dropLast(file.name.length + 1)
                    val fullPackage = "$packageName.${if (subPackageName.isBlank()) "" else "$subPackageName."}"
                    val clazzName = file.name.dropLast(classExtensionLength)
                    Class.forName("$fullPackage$clazzName", true, this).kotlin
                }
        }

    inner class GradleLogger : SqlLogger {
        override fun log(context: StatementContext, transaction: Transaction) {
            if (parameters.debug) {
                logger.debug(context.expandArgs(transaction))
            }
        }
    }

    private enum class SupportedImage(vararg val prefixes: String) {
        MYSQL("mysql:"),
        MARIADB("mariadb:"),
        POSTGRES("postgres:"),
        SQLSERVER("mcr.microsoft.com/mssql/server:"),
        ORACLE("container-registry.oracle.com/", "gvenzl/oracle-", "oracle/");

        fun prefixMatches(name: String): Boolean = prefixes.any { prefix -> name.startsWith(prefix) }

        companion object {
            val supportedPrefixesMessage: String
                get() = "Supported prefixes are: ${entries.joinToString { si -> si.prefixes.joinToString { it } }}"
        }
    }
}
