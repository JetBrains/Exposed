package org.jetbrains.exposed.v1.plugin.core.tables.schema

import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.plugin.core.tables.ColumnMapping

/**
 * Matches the configured [mappings] against the tables and columns that the database reports, and fails when a
 * mapping either matches no column, or matches more than one. This ensures that a mistake in a key is always
 * reported before anything is written.
 *
 * Each part of a key is matched as given and folded to the database's default identifier case through [identifierManager].
 */
internal class ColumnMappingMatcher(
    private val mappings: List<ColumnMapping>,
    private val identifierManager: IdentifierManagerApi,
) {
    /**
     * Checks that every mapping names one of [tables] before any column is read.
     * @throws IllegalStateException if a mapping names a table that is not in the list.
     */
    fun checkTablesExist(tables: List<TableReference>) {
        val unmatched = mappings.filter { mapping ->
            tables.none { mapping.matchesTable(it) }
        }
        if (unmatched.isNotEmpty()) {
            error(
                "Generation aborted (no files written) because a column mapping names a table that was not found:\n" +
                    unmatched.joinToString("\n") { "  * '${it.column}'" } + "\n" +
                    "The key is TABLE.COLUMN for a table in the current schema, or SCHEMA.TABLE.COLUMN for a table in another " +
                    "searched schema. Names are matched as provided so they must be in the database's default identifier case"
            )
        }
    }

    /**
     * Checks that every mapping was matched to a column found in [tables].
     * @throws IllegalStateException if a mapping names a column that was not found in the list.
     */
    fun checkColumnsExist(tables: List<TableMetadata>) {
        val matched = tables.flatMapTo(hashSetOf()) { table ->
            table.columns.mapNotNull { it.mapping }
        }
        val unmatched = mappings.filterNot { it in matched }
        if (unmatched.isNotEmpty()) {
            error(
                "Generation aborted (no files written) because a column mapping names a column that was not found:\n" +
                    unmatched.joinToString("\n") { "  * '${it.column}'" } + "\n" +
                    "Column names are matched as provided so they must be in the database's default identifier case"
            )
        }
    }

    /**
     * Returns the configured mapping of the column [rawColumnName] of [table], or `null` when there is none.
     * @throws IllegalStateException if more than one mapping matches the column.
     */
    fun mappingFor(table: TableReference, rawColumnName: String): ColumnMapping? {
        val matching = mappings.filter {
            it.matchesTable(table) && it.columnPart.matchesIdentifier(rawColumnName)
        }
        check(matching.size <= 1) {
            "Generation aborted (no files written) because several column mappings match column '$rawColumnName' " +
                "of table '${table.name}': ${matching.joinToString { "'${it.column}'" }}"
        }
        return matching.singleOrNull()
    }

    /** Whether this mapping's schema and table parts name [table]. A key without a schema part names the current schema. */
    private fun ColumnMapping.matchesTable(table: TableReference): Boolean {
        val schemaMatches = schemaPart?.matchesIdentifier(table.schema) ?: (table.schemaName == null)
        return schemaMatches && tablePart.matchesIdentifier(table.tableName)
    }

    private fun String.matchesIdentifier(raw: String): Boolean {
        val literal = removeSurrounding(identifierManager.quoteString)
        return literal == raw || identifierManager.inProperCase(literal) == raw
    }
}
