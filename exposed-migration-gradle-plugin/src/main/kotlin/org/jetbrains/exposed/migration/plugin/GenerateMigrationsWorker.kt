package org.jetbrains.exposed.migration.plugin

import org.flywaydb.core.Flyway
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.OracleContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.io.File.separator
import java.net.URL
import java.net.URLClassLoader
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

interface GenerateMigrationsParameters : WorkParameters {
    val migrationsDir: DirectoryProperty
    var exposedTablesPackage: String
    var migrationFilePrefix: String
    var migrationFileSeparator: String
    var migrationFileExtension: String
    var databaseUrl: String?
    var databaseUser: String?
    var databasePassword: String?
    var testContainersImageName: String?
    var classpathUrls: List<URL>
    var debug: Boolean
}

abstract class GenerateMigrationsWorker : WorkAction<GenerateMigrationsParameters> {
    private val logger: Logger = Logging.getLogger(GenerateMigrationsWorker::class.java)
    private val versionPattern by lazy {
        Pattern.compile("^${parameters.migrationFilePrefix}(\\d+)${parameters.migrationFileSeparator}.*$")
    }
    private val versionXYPattern by lazy {
        Pattern.compile("^${parameters.migrationFilePrefix}(\\d+)_(\\d+)${parameters.migrationFileSeparator}.*$")
    }
    private val clasExtensionLength: Int = ".class".length

    override fun execute() {
        val params = parameters
        val extension = params.migrationFileExtension
        val migrationsDirectory = params.migrationsDir.get().asFile
        if (!migrationsDirectory.exists()) migrationsDirectory.mkdirs()
        val versionGen = findHighestVersion(migrationsDirectory)

        val generated = withClassloader { classloader ->
            withDatabase { database ->
                var ignored = 0
                classloader.getClassesInPackage(params.exposedTablesPackage)
                    .mapNotNull { it.tableOrNull() }
                    .mapIndexedNotNull { index, table ->
                        transaction(database) {
                            addLogger(GradleLogger())
                            val statements =
                                MigrationUtils.statementsRequiredForDatabaseMigration(
                                    table,
                                    withLogs = params.debug
                                )
                            if (statements.isNotEmpty()) {
                                val name = statements.first().statementToFileName()
                                val version = versionGen(index - ignored)
                                val fileName = "$version$name$extension"
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

    @Suppress("NestedBlockDepth")
    fun findHighestVersion(migrationsDirectory: File): (Int) -> String {
        var highestMajor = 0
        var hasXYFormat = true

        migrationsDirectory.listFiles()?.forEach { file ->
            val fileName = file.name

            // Check for VX_Y__ format first
            val matcherXY = versionXYPattern.matcher(fileName)
            if (matcherXY.matches()) {
                val major = matcherXY.group(1).toInt()

                if (major > highestMajor || (major == highestMajor)) {
                    highestMajor = major
                }
            } else {
                // Check for VX__ format
                val matcher = versionPattern.matcher(fileName)
                if (matcher.matches()) {
                    hasXYFormat = false
                    val version = matcher.group(1).toInt()
                    if (version > highestMajor) {
                        highestMajor = version
                    }
                }
            }
        }
        highestMajor++

        return if (hasXYFormat) {
            { index: Int -> "${parameters.migrationFilePrefix}${highestMajor}_${index}${parameters.migrationFileSeparator}" }
        } else {
            { index: Int -> "${parameters.migrationFilePrefix}${highestMajor}${parameters.migrationFileSeparator}" }
        }
    }

    private inline fun <A> withDatabase(block: (Database) -> A): A =
        if (parameters.testContainersImageName != null) {
            container(parameters.testContainersImageName!!).use { container ->
                withDatabase(container.jdbcUrl, container.username, container.password) { database ->
                    val migrationsDirectory = parameters.migrationsDir.get().asFile
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
                parameters.databaseUrl == null ||
                    parameters.databaseUser == null ||
                    parameters.databasePassword == null
            ) {
                "Database properties (url, user, password) must be provided when not using TestContainers"
            }
            withDatabase(parameters.databaseUrl!!, parameters.databaseUser!!, parameters.databasePassword!!, block)
        }

    fun container(imageName: String): JdbcDatabaseContainer<*> =
        when {
            imageName.startsWith("postgres:") -> PostgreSQLContainer<Nothing>(imageName)
            imageName.startsWith("mysql:") -> MySQLContainer<Nothing>(imageName)
            imageName.startsWith("mariadb:") -> MariaDBContainer<Nothing>(imageName)
            imageName.startsWith("oracle:") || imageName.startsWith("gvenzl/oracle-xe:") -> OracleContainer(imageName)
            imageName.startsWith("mcr.microsoft.com/mssql/server:") || imageName.startsWith("sqlserver:") ->
                MSSQLServerContainer<Nothing>(imageName)

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

    private fun KClass<*>.tableOrNull(): Table? =
        if (isSubclassOf(Table::class) && !isAbstract) {
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
                    val clazzName = file.name.dropLast(clasExtensionLength)
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
