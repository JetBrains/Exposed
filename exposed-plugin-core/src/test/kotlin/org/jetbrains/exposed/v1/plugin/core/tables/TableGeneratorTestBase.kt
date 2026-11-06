package org.jetbrains.exposed.v1.plugin.core.tables

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URL
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertTrue

@OptIn(InternalApi::class)
abstract class TableGeneratorTestBase {
    @field:TempDir
    protected lateinit var outputDirectory: File

    protected val databaseUrl: String = "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"

    protected val messages = mutableListOf<String>()

    protected val logger: MigrationLogger = object : MigrationLogger {
        override fun lifecycle(message: String) {
            messages.add(message)
        }
        override fun debug(message: String) = Unit
        override val isDebugEnabled: Boolean = false
    }

    @AfterEach
    fun shutdownDatabase() {
        execute("SHUTDOWN")
    }

    /** Returns a list of file paths (relative to `TableConfig.fileDirectory`) that were written. */
    @Suppress("LongParameterList")
    protected fun generate(
        tablesPackage: String = "com.example.tables",
        fullFileName: String? = null,
        uuidColumnType: UuidColumnType = UuidColumnType.KOTLIN,
        objectNameSuffix: String = "",
        schemas: List<String> = emptyList(),
        dateTimeColumnType: DateTimeColumnType? = null,
        classpathUrls: List<URL> = emptyList(),
        columnMappings: List<ColumnMapping> = emptyList(),
        generateIdTables: Boolean = false,
    ): List<String> {
        val generator = TableGenerator(
            config = TableConfig(
                tablesPackage = tablesPackage,
                classpathUrls = classpathUrls,
                fileDirectory = outputDirectory,
                fullFileName = fullFileName,
                schemas = schemas,
                objectNameSuffix = objectNameSuffix,
                uuidColumnType = uuidColumnType,
                dateTimeColumnType = dateTimeColumnType,
                generateIdTables = generateIdTables,
                columnMappings = columnMappings,
                databaseUrl = databaseUrl,
                databaseUser = "",
                databasePassword = "",
            ),
            logger = logger,
        )
        return generator.generate()
    }

    protected fun clearGeneratedFiles() {
        File(outputDirectory, "com").deleteRecursively()
    }

    protected fun path(fileName: String): String = "com${File.separator}example${File.separator}tables${File.separator}$fileName.kt"

    /** Returns the generated source with all whitespace collapsed, so that KotlinPoet line-wrapping does not affect assertions. */
    protected fun generatedSource(relativePath: String): String {
        val file = File(outputDirectory, relativePath)
        assertTrue(file.isFile, "Expected generated file at ${file.absolutePath}")
        return file.readText().replace(Regex("\\s+"), " ")
    }

    protected fun List<String>.sourceOf(objectName: String): String = generatedSource(first { it.endsWith("${File.separator}$objectName.kt") })

    protected fun assertLogged(prefix: String) {
        assertTrue(messages.any { it.startsWith(prefix) }, "Expected a message starting with '$prefix': $messages")
    }

    protected fun assertWarning(prefix: String) = assertLogged("Warning: $prefix")

    protected fun execute(vararg statements: String) {
        DriverManager.getConnection(databaseUrl, "", "").use { connection ->
            connection.createStatement().use { statement ->
                statements.forEach { statement.execute(it) }
            }
        }
    }
}
