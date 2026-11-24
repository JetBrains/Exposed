package org.jetbrains.exposed.v1.migration.core

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.SchemaUtilityApi
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.transactions.currentTransaction
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import java.io.File

/**
 * Base class representing helper functions necessary for performing database schema migrations.
 */
abstract class MigrationUtilityApi : SchemaUtilityApi() {
    /**
     * Appends this list of SQL statements to a File at [filePath] and returns the File.
     * @suppress
     */
    @InternalApi
    protected fun List<String>.writeMigrationScriptTo(filePath: String): File {
        val migrationScript = File(filePath)
        migrationScript.createNewFile()
        // Clear existing content
        migrationScript.writeText("")
        // Append statements
        forEach { statement ->
            if (statement.isBlank()) return@forEach
            // Add semicolon only if it's not already there
            val conditionalSemicolon = if (statement.last() == ';') "" else ";"
            migrationScript.appendText("$statement$conditionalSemicolon\n")
        }
        return migrationScript
    }

    /**
     * Adds DROP statements for all columns that exist in the database, but not this table mapping, to [destination].
     * @suppress
     */
    @InternalApi
    protected fun <C : MutableCollection<String>> Table.mapUnmappedColumnStatementsTo(
        destination: C,
        existingColumns: List<ColumnMetadata>
    ): C {
        val mappedColumns = existingColumns.mapNotNull { columnMetadata ->
            val mappedCol = columns.find { column ->
                columnMetadata.name.equals(column.nameUnquoted(), true)
            }
            if (mappedCol != null) columnMetadata else null
        }
        val unmappedColumns = existingColumns.filter { it !in mappedColumns }
        val tr = currentTransaction()
        unmappedColumns.forEach {
            destination.add(
                "ALTER TABLE ${tr.identity(this)} DROP COLUMN " +
                    tr.db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.name)
            )
        }
        return destination
    }

    /**
     * Filters this set of sequence names and returns a [Sequence] for any missing from the database.
     * @suppress
     */
    @InternalApi
    protected fun Set<String>.filterMissingSequences(vararg tables: Table): List<Sequence> {
        val missingSequences = mutableSetOf<Sequence>()
        val mappedSequences = tables.flatMap { table -> table.sequences }.toSet()
        val existingSequenceNames = map { it.unqualifiedSequenceName().inProperCase() }.toSet()
        missingSequences.addAll(
            mappedSequences.filterNot { it.identifier.unqualifiedSequenceName().inProperCase() in existingSequenceNames }
        )
        return missingSequences.toList()
    }

    /**
     * Filters this set of sequence names and returns a [Sequence] for any present in the database,
     * but not defined on a table object.
     * @suppress
     */
    @InternalApi
    protected fun Set<String>.filterUnmappedSequences(vararg tables: Table): List<Sequence> {
        val mappedSequencesNames = tables.flatMap { table ->
            table.sequences.map {
                it.identifier.unqualifiedSequenceName().inProperCase()
            }
        }.toSet()

        return filter { !mappedSequencesNames.contains(it.unqualifiedSequenceName().inProperCase()) }
            .map { Sequence(it) }
    }

    /**
     * Returns this sequence name without any schema qualification, so that sequence names reported by the database,
     * which may omit the schema prefix (EXPOSED-985), can be compared against sequence identifiers mapped in code,
     * which may carry one. Quoted names are stripped of their wrapping quote characters before splitting.
     */
    private fun String.unqualifiedSequenceName(): String {
        val unquoted = if (startsWith("\"") && endsWith("\"")) removeSurrounding("\"") else this
        return unquoted.substringAfterLast('.', unquoted)
    }
}
