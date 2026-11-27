package org.jetbrains.exposed.v1.plugin.core.tables

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationLogger
import org.jetbrains.exposed.v1.plugin.core.tables.codegen.GeneratedTable
import org.jetbrains.exposed.v1.plugin.core.tables.codegen.GenerationContext
import org.jetbrains.exposed.v1.plugin.core.tables.codegen.INFERRED_TYPE_PLACEHOLDER
import org.jetbrains.exposed.v1.plugin.core.tables.codegen.TableCodeBuilder
import org.jetbrains.exposed.v1.plugin.core.tables.codegen.TablePlanner
import org.jetbrains.exposed.v1.plugin.core.tables.schema.SchemaReader
import java.io.File
import java.net.URLClassLoader
import java.sql.Connection

/**
 * Build-tool-agnostic core of the Exposed table generation plugin.
 *
 * Given a [TableConfig] and a [MigrationLogger], this connects to an existing database and reads the metadata of every
 * table in the connection's current schema (or other schemas, if [TableConfig.schemas] is set). It writes one Kotlin
 * source file per table (or a single file, if [TableConfig.fullFileName] is set) containing the equivalent `Table`
 * object definitions to [TableConfig.fileDirectory], under the subdirectory matching [TableConfig.tablesPackage].
 *
 * Consumed by:
 *  - `exposed-gradle-plugin`'s `GenerateTablesWorker`
 *  - `exposed-maven-plugin`'s `GenerateTableMojo`
 *
 * Pure JVM; no Gradle or Maven types appear on its API surface.
 * @suppress
 */
@InternalApi
class TableGenerator(
    private val config: TableConfig,
    private val logger: MigrationLogger,
) {
    /** The directory matching [TableConfig.tablesPackage] below [TableConfig.fileDirectory], where files are written. */
    private val packageDirectory: File = File(config.fileDirectory, config.tablesPackage.replace('.', File.separatorChar))

    /**
     * Runs the generator end-to-end.
     *
     * @return List of file paths (relative to [TableConfig.fileDirectory]) that were written.
     * @throws IllegalArgumentException if a necessary configuration is omitted or malformed, for example:
     *     If the package name for the generated table definitions is not configured;
     *     If any of the database connection properties (url, user, password) is not configured;
     *     If [TableConfig.fullFileName] contains a path separator;
     *     If a column mapping's key is not of the form `TABLE.COLUMN` or `SCHEMA.TABLE.COLUMN`.
     * @throws IllegalStateException if the output cannot be written without conflicts, or if
     *     a column mapping matches no column or several; nothing is written in that case.
     * @see TablePlanner.plan
     */
    fun generate(): List<String> {
        require(config.tablesPackage.isNotBlank()) { "Package name for generated Exposed table definitions must be set" }

        val singleFileName = config.fullFileName?.validateFileName()
        config.columnMappings.forEach { it.keyParts }
        val planner = TablePlanner(packageDirectory, config.objectNameSuffix, singleFileName, logger)

        val (plannedTables, schema) = withDatabase { database ->
            transaction(database) {
                val reader = SchemaReader(connection.connection as Connection, config.schemas, logger, config.columnMappings)
                val plannedTables = planner.plan(reader.listTables())
                plannedTables to reader.readAllTables(plannedTables.map { it.table })
            }
        }
        val objectNames = plannedTables.associate { it.table to it.objectName }
        logger.debug("Found ${schema.tables.size} table(s) in the searched database schemas: ${schema.tables.joinToString { it.name }}")
        schema.unreferencedSequences.forEach { sequence ->
            logger.warn(
                "Sequence '$sequence' is not used as the default value of any generated column. " +
                    "If a column takes its values from it through Exposed's autoIncrement(\"$sequence\"), add that call manually. " +
                    "Otherwise, generateMigrations would propose dropping the sequence"
            )
        }

        // the classpath is consulted twice: to pick the date-time module, and to warn about modules the output needs
        val generatedTables = URLClassLoader(config.classpathUrls.toTypedArray(), null).use { classpath ->
            val dateTimeColumnType = resolveDateTimeColumnType(classpath)
            val context = GenerationContext(
                schema.tables,
                objectNames,
                schema.syntax,
                config.uuidColumnType,
                dateTimeColumnType,
                config.generateIdTables,
                logger,
            )
            context.referenceCycles.forEach { cycle ->
                logger.warn(
                    "Tables ${cycle.joinToString { "'${it.name}'" }} reference each other in a cycle. Kotlin objects that reference each " +
                        "other while initializing fail at first use, and Exposed has no deferred reference, so remove one of the generated " +
                        "references by hand"
                )
            }
            schema.tables.map { table ->
                TableCodeBuilder(table, objectNames.getValue(table.reference), context, logger).build()
            }.also { warnOnMissingModules(it, classpath) }
        }

        val fileSpecs = if (singleFileName != null) {
            listOf(fileSpec(singleFileName, generatedTables))
        } else {
            generatedTables.map { fileSpec(it.typeSpec.name!!, listOf(it)) }
        }

        val generated = fileSpecs.map { it.writeTo() }

        logger.lifecycle("")
        logger.lifecycle("# Exposed Tables Generated ${generatedTables.size} table objects in ${generated.size} file(s):")
        generated.forEach { logger.lifecycle("  * $it") }
        logger.lifecycle("")

        return generated
    }

    /**
     * Returns the configured [TableConfig.dateTimeColumnType], or the module detected on [TableConfig.classpathUrls],
     * or [DateTimeColumnType.KOTLIN_DATETIME] when neither is available. The choice is logged unless configured explicitly.
     */
    private fun resolveDateTimeColumnType(classpath: ClassLoader): DateTimeColumnType {
        config.dateTimeColumnType?.let { return it }

        val detected = DateTimeColumnType.entries.filter { it.module.isOnClasspath(classpath) }
        return when (detected.size) {
            0 -> DateTimeColumnType.KOTLIN_DATETIME.also {
                logger.lifecycle(
                    "No Exposed date-time module was found on the classpath. date-time columns are generated for ${it.module.artifactName}. " +
                        "Set dateTimeColumnType to choose a module explicitly"
                )
            }
            1 -> detected.single().also {
                logger.lifecycle("Detected ${it.module.artifactName} on the classpath. date-time columns are generated for it")
            }
            else -> detected.first().also {
                logger.warn(
                    "Several Exposed date-time modules were found on the classpath (${detected.joinToString { m -> m.module.artifactName }}). " +
                        "date-time columns are generated for ${it.module.artifactName}. Set dateTimeColumnType to choose a module explicitly"
                )
            }
        }
    }

    private fun warnOnMissingModules(tables: List<GeneratedTable>, classpath: ClassLoader) {
        tables.flatMapTo(linkedSetOf()) { it.requiredModules }
            .filterNot { it.isOnClasspath(classpath) }
            .forEach { module ->
                logger.warn(
                    "The generated definitions use column functions from ${module.artifactName}, which was not found on the classpath. " +
                        "Add it as a dependency so that they compile"
                )
            }
    }

    private fun fileSpec(name: String, tables: List<GeneratedTable>): FileSpec {
        val builder = FileSpec.builder(config.tablesPackage, name)
            .indent(INDENT)
            .addKotlinDefaultImports(includeJvm = true)
            .addFileComment("Generated by the Exposed plugin from an existing database schema.")
        val requiredOptIns = tables.flatMapTo(linkedSetOf()) { it.requiredOptIns }
        if (requiredOptIns.isNotEmpty()) {
            val optIn = AnnotationSpec.builder(OPT_IN)
            requiredOptIns.forEach { optIn.addMember("%T::class", it) }
            builder.addAnnotation(optIn.build())
        }
        tables.flatMapTo(linkedSetOf()) { it.requiredImports }.validatedImports(name).forEach { import ->
            builder.addImport(import.substringBeforeLast('.'), import.substringAfterLast('.'))
        }
        tables.forEach { builder.addType(it.typeSpec) }
        return builder.build()
    }

    /**
     * Returns these configured imports after checking that no two of them import the same simple name from different
     * packages, which Kotlin rejects. The generated file is not compiled at this point. The imports themselves are not verified.
     */
    private fun Set<String>.validatedImports(fileName: String): Set<String> {
        val conflicts = groupBy { it.substringAfterLast('.') }.filterValues { it.size > 1 }
        check(conflicts.isEmpty()) {
            "Generation aborted (no files written because the column mappings generated into '$fileName$CODE_EXTENSION' " +
                "import the same name from different packages: " +
                conflicts.values.joinToString("; ") { imports -> imports.joinToString(", ") }
        }
        return this
    }

    /**
     * Writes this file spec below [TableConfig.fileDirectory] and returns the written path relative to that directory.
     * Explicit `public` modifiers emitted by KotlinPoet are removed so that the output matches idiomatic Exposed code,
     * and the placeholder type of mapped properties is removed so that Kotlin infers it from the configured code.
     */
    private fun FileSpec.writeTo(): String {
        val file = File(packageDirectory, name + CODE_EXTENSION)
        file.parentFile.mkdirs()
        val source = toString()
            .replace(PUBLIC_MODIFIER_REGEX, "$1")
            .replace(INFERRED_TYPE_REGEX, " =")
            .replace(INFERRED_TYPE_IMPORT_REGEX, "")
        file.writeText(source)
        return file.relativeTo(config.fileDirectory).path
    }

    private fun String.validateFileName(): String {
        require(!contains('/') && !contains('\\')) { "Provided fileName must not contain path separators: $this" }
        return removeSuffix(CODE_EXTENSION)
    }

    private inline fun <A> withDatabase(block: (Database) -> A): A {
        require(
            config.databaseUrl != null &&
                config.databaseUser != null &&
                config.databasePassword != null
        ) {
            "Database properties (url, user, password) must be provided"
        }
        val db = Database.connect(url = config.databaseUrl, user = config.databaseUser, password = config.databasePassword)
        return try {
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    private companion object {
        const val CODE_EXTENSION = ".kt"
        const val INDENT = "    "
        val PUBLIC_MODIFIER_REGEX = Regex("^(\\s*)public ", RegexOption.MULTILINE)
        val INFERRED_TYPE_REGEX = Regex(":\\s*${Regex.escape(INFERRED_TYPE_PLACEHOLDER.simpleName)}\\s*=")

        /** KotlinPoet imports a default-package class by its bare name, so the placeholder's import line goes too. */
        val INFERRED_TYPE_IMPORT_REGEX = Regex("^import ${Regex.escape(INFERRED_TYPE_PLACEHOLDER.simpleName)}\\r?\\n", RegexOption.MULTILINE)
        val OPT_IN = ClassName("kotlin", "OptIn")
    }
}
