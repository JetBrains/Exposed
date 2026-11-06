package org.jetbrains.exposed.v1.plugin.core.tables.codegen

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationLogger
import org.jetbrains.exposed.v1.plugin.core.tables.schema.TableReference
import org.jetbrains.exposed.v1.plugin.core.tables.toObjectName
import org.jetbrains.exposed.v1.plugin.core.tables.warn
import java.io.File

/**
 * Responsible for deciding which Kotlin object each database table becomes and whether the target package can take it.
 * This is done from when table names are retrieved, before any column is fetched or any file is written.
 */
@OptIn(InternalApi::class)
internal class TablePlanner(
    /** The directory matching the target package below the output directory, which may not exist yet. */
    private val packageDirectory: File,
    private val objectNameSuffix: String,
    /** The single file every object is written to (without extension), or `null` for one file per object. */
    private val singleFileName: String?,
    private val logger: MigrationLogger,
) {
    /**
     * Returns the object name of every table, in the given order.
     *
     * @throws IllegalStateException if two tables would be generated as the same object, or if a file that would be
     *   written already exists, whether it declares the expected object or not.
     */
    fun plan(tables: List<TableReference>): List<PlannedTable> {
        val planned = tables.map { PlannedTable(it, it.toObjectName()) }
        failOnDuplicateObjectNames(planned)
        failOnExistingOutputFiles(planned)
        warnOnRedeclaredTableObjects(planned)
        return planned
    }

    private fun TableReference.toObjectName(): String =
        schemaName?.toObjectName().orEmpty() + simpleName.toObjectName() + objectNameSuffix

    private fun outputFile(name: String): File = File(packageDirectory, name + CODE_EXTENSION)

    private fun failOnDuplicateObjectNames(planned: List<PlannedTable>) {
        val duplicates = planned.groupBy { it.objectName }.filterValues { it.size > 1 }
        if (duplicates.isEmpty()) return

        val problems = duplicates.map { (objectName, tables) ->
            "Tables ${tables.joinToString { "'${it.table.name}'" }} would all be generated as object '$objectName'"
        }
        error(
            "Generation aborted (no files written) because the generated code would not compile:\n" +
                problems.joinToString("\n") { "  * $it" } + "\n" +
                "Rename one of the tables or restrict the searched schemas so that only one of them is generated"
        )
    }

    private fun failOnExistingOutputFiles(planned: List<PlannedTable>) {
        val expectedObjects: Map<File, List<String>> = if (singleFileName != null) {
            mapOf(outputFile(singleFileName) to planned.map { it.objectName })
        } else {
            planned.associate { outputFile(it.objectName) to listOf(it.objectName) }
        }
        val problems = expectedObjects.filterKeys { it.isFile }.map { (file, objectNames) ->
            val source = file.readText().stripComments()
            val declared = objectNames.filter { source.declaresClassifier(it) }
            if (declared.isNotEmpty()) {
                "File '${file.path}' already declares ${declared.joinToString { "'$it'" }}. " +
                    "Move or rename ${if (declared.size == 1) "the file" else "them"} so that the generated definition can be written"
            } else {
                "File '${file.path}' already exists but does not declare ${objectNames.joinToString { "'$it'" }}. " +
                    "Move or rename the file so that the generated definition can be written"
            }
        }
        if (problems.isEmpty()) return

        error(
            "Generation aborted (no files written) because generateTables never overwrites existing files:\n" +
                problems.joinToString("\n") { "  * $it" }
        )
    }

    /**
     * Warns about every top-level `object` extending a `Table` class in the package directory whose name a generated
     * object would redeclare.
     * The compiler remains the authority, and the warning only exists so that `objectNameSuffix` can be set first.
     */
    private fun warnOnRedeclaredTableObjects(planned: List<PlannedTable>) {
        val objectNames = planned.mapTo(hashSetOf()) { it.objectName }
        val sources = packageDirectory.listFiles { file -> file.isFile && file.extension == "kt" }.orEmpty()
        sources.sortedBy { it.name }.forEach { source ->
            TABLE_OBJECT_PATTERN.findAll(source.readText().stripComments()).forEach { match ->
                val name = match.groupValues[1].ifEmpty { match.groupValues[2] }
                if (name in objectNames) {
                    logger.warn(
                        "Object '$name' extending Table is already declared in '${source.name}'; the generated object '$name' " +
                            "would redeclare it and the package would not compile. Set objectNameSuffix or move or rename the existing object"
                    )
                }
            }
        }
    }

    private companion object {
        const val CODE_EXTENSION = ".kt"

        val BLOCK_COMMENT: Regex = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT: Regex = Regex("""//.*""")

        val TABLE_OBJECT_PATTERN: Regex = Regex(
            """^(?:\w+[ \t]+)*object[ \t]+(?:`([^`]+)`|(\w+))\s*:(?:(?!\n[ \t]*\n)[^{])*?\b\w*Table\b""",
            RegexOption.MULTILINE
        )

        fun String.stripComments(): String = replace(BLOCK_COMMENT, "").replace(LINE_COMMENT, "")

        fun String.declaresClassifier(name: String): Boolean {
            val escaped = Regex.escape(name)
            return Regex("""^(?:\w+[ \t]+)*(?:object|class|interface|typealias)[ \t]+(?:`$escaped`|$escaped)\b""", RegexOption.MULTILINE)
                .containsMatchIn(this)
        }
    }
}

/** A table bound for generation together with the name of the Kotlin object it is generated as. */
internal data class PlannedTable(val table: TableReference, val objectName: String)
