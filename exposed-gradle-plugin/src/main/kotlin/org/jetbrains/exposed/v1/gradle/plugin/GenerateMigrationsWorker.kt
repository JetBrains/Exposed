package org.jetbrains.exposed.v1.gradle.plugin

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
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
import kotlin.time.ExperimentalTime

/**
 * Represents the implementation of a unit of work to be used when submitting work to the migrations extension work executor.
 */
abstract class GenerateMigrationsWorker : WorkAction<GenerateMigrationsParameters> {
    private val logger: Logger = Logging.getLogger(GenerateMigrationsWorker::class.java)
    private val classExtensionLength: Int = ".class".length

    // format like V3__description or V0003_description
    private val versionXPattern by lazy {
        Regex("^${parameters.filePrefix}(\\d+)${parameters.fileSeparator}.*$")
    }

    // format like V3_1__description or V0003_001_description
    private val versionXYPattern by lazy {
        Regex("^${parameters.filePrefix}(\\d+)_(\\d+)${parameters.fileSeparator}.*$")
    }

    // format like V3_YYYYMMDDHHMMSS__description or V003_YYYYMMDDHHMMSS__description or V3_YYYYMMDDHHMM__description
    private val versionXTSPattern by lazy {
        Regex("^${parameters.filePrefix}(\\d+)_(\\d{12,14})${parameters.fileSeparator}.*$")
    }

    override fun execute() {
        val params = parameters
        val extension = params.fileExtension
        val migrationsDirectory = params.fileDirectory.get().asFile
        if (!migrationsDirectory.exists()) {
            migrationsDirectory.mkdirs()
        }
        val versionGen = findHighestVersion(migrationsDirectory)

        val generated = withClassloader { classloader ->
            withDatabase { database ->
                var ignored = 0
                classloader
                    .getClassesInPackage(params.tablesPackage)
                    .mapNotNull { it.tableOrNull() }
                    .mapIndexedNotNull { index, table ->
                        transaction(database) {
                            addLogger(GradleLogger())
                            // TODO confirm order <-- this goes through each individual table & creates a script
                            // TODO what if tables have references, as this avoids Exposed implicit sortTablesByReferences?
                            val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
                                table,
                                withLogs = params.debug
                            )
                            if (statements.isNotEmpty()) {
                                val description = statements.first().statementToFileDescription(params.useUpperCaseDescription)
                                val version = versionGen(index - ignored)
                                val fileName = "$version$description$extension"
                                val migrationFile = File(migrationsDirectory, fileName)
                                migrationFile.writeText(statements.joinToString(";\n"))
                                fileName
                            } else {
                                ignored++
                                null
                            }
                        }
                    }.toList()
            }
        }
        logger.lifecycle("")
        logger.lifecycle("# Exposed Migrations Generated ${generated.size} migrations:")
        generated.forEach { logger.lifecycle("  * $it") }
        logger.lifecycle("")
    }

    // TODO should there be an extensions property for versionFormat, to simplify/override this?
    @OptIn(ExperimentalTime::class)
    @Suppress("NestedBlockDepth")
    private fun findHighestVersion(migrationsDirectory: File): (Int) -> String {
        var highestMajor = 0
        var highestVersionLength = 0
        var hasXTSFormat = false
        var hasXYFormat = false
        var hasXFormat = false

        migrationsDirectory.listFiles()?.forEach { file ->
            val fileName = file.name
            var version = 0
            var versionLength = 0

            // TODO should VYYYMMDDHHMMSS__ format also be an option?
            // TODO should VX__ be the fallback default?
            // Check for V#_YYYYMMDDHHMMSS__ format first (also covers formats like V00#_YYYYMMDDHHMMSS__ or without SS)
            // Then check for V#_#__ format second (also covers formats like V00#_00#__)
            // Then check for V#__ format (also covers formats like V00#__)
            versionXTSPattern.matchEntire(fileName)?.let { matcher ->
                hasXTSFormat = true
                val stringVersion = matcher.groupValues[1]
                version = stringVersion.toInt()
                versionLength = stringVersion.length
            }
                ?: versionXYPattern.matchEntire(fileName)?.let { matcher ->
                    hasXTSFormat = false
                    hasXYFormat = true
                    val stringVersion = matcher.groupValues[1]
                    version = stringVersion.toInt()
                    versionLength = stringVersion.length
                }
                ?: versionXPattern.matchEntire(fileName)?.let { matcher ->
                    hasXTSFormat = false
                    hasXYFormat = false
                    hasXFormat = true
                    val stringVersion = matcher.groupValues[1]
                    version = stringVersion.toInt()
                    versionLength = stringVersion.length
                }

            // TODO determine if the boolean flags is what we want; i.e. the last file format always wins??? Or should the first win?
            if (hasXTSFormat || hasXYFormat || hasXFormat) {
                if (version >= highestMajor) {
                    highestMajor = version
                }
                if (versionLength > version.toString().length && versionLength > highestVersionLength) {
                    highestVersionLength = versionLength
                }
            }
        }
        highestMajor++

        return if (hasXTSFormat) {
            { _: Int ->
                val majorPadded = highestMajor.toString().padStart(highestVersionLength, '0')
                "${parameters.filePrefix}${majorPadded}_${getCurrentTimestamp()}${parameters.fileSeparator}"
            }
        } else if (hasXYFormat) {
            { index: Int ->
                val majorPadded = highestMajor.toString().padStart(highestVersionLength, '0')
                val minorPadded = index.toString().padStart(highestVersionLength, '0')
                "${parameters.filePrefix}${majorPadded}_${minorPadded}${parameters.fileSeparator}"
            }
        } else {
            { _: Int ->
                val majorPadded = highestMajor.toString().padStart(highestVersionLength, '0')
                "${parameters.filePrefix}$majorPadded${parameters.fileSeparator}"
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun getCurrentTimestamp(): String {
        val ts = Clock.System.now()
        val customFormat = DateTimeComponents.Format {
            date(LocalDate.Formats.ISO_BASIC)
            hour()
            minute()
            second()
        }
        return ts.format(customFormat)
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
        imageName.startsWith("postgres:") -> PostgreSQLContainer(imageName)
        imageName.startsWith("mysql:") -> MySQLContainer(imageName)
        imageName.startsWith("mariadb:") -> MariaDBContainer(imageName)
        imageName.startsWith("oracle:") || imageName.startsWith("gvenzl/oracle-xe:") -> OracleContainer(imageName)
        imageName.startsWith("mcr.microsoft.com/mssql/server:") || imageName.startsWith("sqlserver:") ->
            MSSQLServerContainer(imageName)

        else -> throw IllegalArgumentException(
            "Unsupported database container image: $imageName. " +
                "Supported prefixes are: postgres:, mysql:, mariadb:, sqlserver:, mcr.microsoft.com/mssql/server:, oracle:, gvenzl/oracle-xe:"
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

    private fun URLClassLoader.getClassesInPackage(packageName: String): Sequence<KClass<*>> =
        getResources(packageName.replace('.', '/')).asSequence().flatMap { resource ->
            File(resource.toURI())
                .walk()
                .filter { file -> file.isFile && file.name.endsWith(".class") }
                .map { file ->
                    val baseDir = File(resource.toURI())
                    val subPackageName = file.relativeTo(baseDir)
                        .path
                        .replace(separator, ".").dropLast(file.name.length + 1)
                    val fullPackage = packageName + "." + if (subPackageName.isBlank()) "" else "$subPackageName."
                    val clazzName = file.name.dropLast(classExtensionLength)
                    Class.forName("$fullPackage$clazzName", true, this).kotlin
                }
        }

    inner class GradleLogger : SqlLogger {
        override fun log(context: StatementContext, transaction: Transaction) {
            if (parameters.debug) {
                logger.debug(context.expandArgs(TransactionManager.current()))
            }
        }
    }
}
