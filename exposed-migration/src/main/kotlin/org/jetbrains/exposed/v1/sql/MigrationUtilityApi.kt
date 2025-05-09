package org.jetbrains.exposed.v1.sql

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.SchemaUtilityApi
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.io.File

abstract class MigrationUtilityApi : SchemaUtilityApi() {
    /** Appends this list of SQL statements to a File at [filePath] and returns the File. */
    @InternalApi
    protected fun List<String>.writeMigrationScriptTo(filePath: String): File {
        val migrationScript = File(filePath)
        migrationScript.createNewFile()
        // Clear existing content
        migrationScript.writeText("")
        // Append statements
        forEach { statement ->
            // Add semicolon only if it's not already there
            val conditionalSemicolon = if (statement.last() == ';') "" else ";"
            migrationScript.appendText("$statement$conditionalSemicolon\n")
        }
        return migrationScript
    }

    /** Adds DROP statements for all columns that exist in the database, but not this table mapping, to [destination]. */
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
        val tr = TransactionManager.Companion.current()
        unmappedColumns.forEach {
            destination.add(
                "ALTER TABLE ${tr.identity(this)} DROP COLUMN " +
                    tr.db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.name)
            )
        }
        return destination
    }

    /** Filters this set of sequence names and returns a [Sequence] for any missing from the database. */
    @InternalApi
    protected fun Set<String>.filterMissingSequences(vararg tables: Table): List<Sequence> {
        val missingSequences = mutableSetOf<Sequence>()
        val mappedSequences = tables.flatMap { table -> table.sequences }.toSet()
        missingSequences.addAll(mappedSequences.filterNot { it.identifier.inProperCase() in this })
        return missingSequences.toList()
    }

    /**
     * Filters this set of sequence names and returns a [Sequence] for any present in the database,
     * but not defined on a table object.
     */
    @InternalApi
    protected fun Set<String>.filterUnmappedSequences(vararg tables: Table): List<Sequence> {
        val unmappedSequences = mutableSetOf<Sequence>()
        val mappedSequencesNames = tables.flatMap { table -> table.sequences.map { it.identifier.inProperCase() } }.toSet()
        unmappedSequences.addAll(subtract(mappedSequencesNames).map { Sequence(it) })
        return unmappedSequences.toList()
    }

    /** Returns an identity in a casing appropriate for its identifier status and the database, then caches the returned value. */
    @InternalApi
    protected fun String.inProperCase(): String =
        TransactionManager.Companion.currentOrNull()?.db?.identifierManager?.inProperCase(this@inProperCase) ?: this
}
