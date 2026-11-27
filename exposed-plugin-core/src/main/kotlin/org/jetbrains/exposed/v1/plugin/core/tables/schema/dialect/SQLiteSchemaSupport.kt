package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet

/**
 * Responsible for obtaining non-standard metadata from an SQLite database.
 *
 * SQLite has no schemas, sequences or enumeration types. Its driver reports only the type affinity of a column and
 * parses the names of foreign keys out of the table's DDL unreliably. This means that the declared column types,
 * the key columns, and the foreign keys are all read through the database's own `PRAGMA` functions instead.
 */
internal object SQLiteSchemaSupport : DialectSchemaSupport {
    override val syntax: DialectSyntax = SQLiteSyntax

    override val createsIndexForForeignKeys: Boolean = false

    override val supportsSchemas: Boolean = false

    override val schemasAreCatalogs: Boolean = false

    override fun isSystemSchema(name: String): Boolean = false

    override fun declaredColumnTypes(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return tableColumns(connection, tableName).associate { it.name to normalizeDeclaredType(it.type) }
    }

    override fun generatedColumns(connection: Connection, schema: String, tableName: String): Map<String, String> {
        val generated = tableColumns(connection, tableName).filter {
            it.hidden == VIRTUAL_COLUMN || it.hidden == STORED_COLUMN
        }
        if (generated.isEmpty()) return emptyMap()
        val ddl = tableDdl(connection, tableName)
        return generated.associate { it.name to generationExpression(ddl, it.name).orEmpty() }
    }

    // The name of a `UNIQUE` constraint is not reported anywhere but in the table's DDL.
    // The internal index that backs the constraint is its only trace in the metadata.
    // See [SQLiteSyntax.isInternalIndexName]
    override fun uniqueConstraintNames(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, String> = emptyMap()

    override fun sequenceParameters(
        connection: Connection,
        schema: String,
        sequenceName: String,
        sequenceMaxValue: Long
    ): SequenceParameters? = null

    override fun ownedSequences(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, String> = emptyMap()

    override fun enumerationTypes(connection: Connection, schema: String): Map<String, List<String>> = emptyMap()

    // Read from `PRAGMA foreign_key_list`, which numbers the keys of a table, rather than from the driver, which
    // reports an empty name for every unnamed key and may attach a declared name to the wrong key.
    // TODO consider changing core migration utils to a custom query like this
    override fun foreignKeys(connection: Connection, schema: String, tableName: String): List<ReportedForeignKeyColumn> {
        val keyColumns = connection.query(
            "SELECT id, seq, \"table\", \"from\", \"to\", on_update, on_delete FROM pragma_foreign_key_list(?) ORDER BY id, seq",
            tableName,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        ForeignKeyColumn(
                            id = rs.getInt("id"),
                            position = rs.getInt("seq"),
                            targetTable = rs.getString("table"),
                            columnName = rs.getString("from"),
                            targetColumnName = rs.getString("to"),
                            updateRule = rs.getString("on_update"),
                            deleteRule = rs.getString("on_delete"),
                        )
                    )
                }
            }
        }
        if (keyColumns.isEmpty()) return emptyList()

        val ownColumnNames = tableColumns(connection, tableName).map { it.name }
        return keyColumns.groupBy { it.id }.flatMap { (id, columns) ->
            val targetTable = tableName(connection, columns.first().targetTable) ?: columns.first().targetTable
            val targetColumns = tableColumns(connection, targetTable)
            // a key that names no referenced columns references the primary key of the table
            val targetKeyColumnNames = targetColumns.filter { it.keyPosition > 0 }
                .sortedBy { it.keyPosition }
                .map { it.name }
            columns.mapIndexed { index, column ->
                val targetColumnName = column.targetColumnName
                    ?.let { reported -> targetColumns.map { it.name }.matching(reported) }
                    ?: targetKeyColumnNames.getOrNull(index).orEmpty()
                ReportedForeignKeyColumn(
                    groupKey = id.toString(),
                    name = "",
                    position = column.position + 1,
                    columnName = ownColumnNames.matching(column.columnName),
                    targetSchema = "",
                    targetTable = targetTable,
                    targetColumnName = targetColumnName,
                    deleteRule = ruleCode(column.deleteRule),
                    updateRule = ruleCode(column.updateRule),
                )
            }
        }
    }

    // A single-column primary key declared as exactly `INTEGER` is an alias of the table's rowid: the database assigns
    // its values whether or not `AUTOINCREMENT` is declared, and it never holds NULL.
    // However, the driver reports this column as nullable and as auto-incremented only with the keyword.
    // Any other primary key column may hold NULL unless it is declared `NOT NULL`, which Exposed cannot declare.
    override fun columnDefaults(connection: Connection, schema: String, tableName: String): Map<String, String?>? = null

    // SQLite has no sequences
    override fun sequences(connection: Connection, schemas: List<String>): List<SequenceReference>? = null

    override fun unsupportedTableNote(connection: Connection, schema: String, tableName: String): String? = null

    override fun columnOverrides(connection: Connection, schema: String, tableName: String): Map<String, ColumnOverride> {
        val keyColumns = tableColumns(connection, tableName).filter { it.keyPosition > 0 }
        if (keyColumns.isEmpty()) return emptyMap()

        val rowIdAlias = keyColumns.singleOrNull()?.takeIf {
            it.type.trim().equals(ROWID_ALIAS_TYPE, ignoreCase = true) &&
                !WITHOUT_ROWID_PATTERN.containsMatchIn(tableDdl(connection, tableName))
        }
        return keyColumns.associate { column ->
            column.name to if (column == rowIdAlias) {
                ColumnOverride(
                    autoIncrement = true,
                    nullable = false,
                    autoIncrementNote = "Column '${column.name}' of table '$tableName' is declared INTEGER PRIMARY KEY without AUTOINCREMENT. " +
                        "The database still assigns its values, so it is generated with autoIncrement(), which declares AUTOINCREMENT",
                )
            } else {
                ColumnOverride(
                    nullable = false,
                    nullableWarning = "Column '${column.name}' of table '$tableName' is part of the primary key but is not declared NOT NULL, " +
                        "so the database lets it hold NULL. Exposed always declares primary key columns NOT NULL, so it is generated as non-nullable",
                )
            }
        }
    }

    /** A column of a table exactly as `PRAGMA table_xinfo` reports it. */
    private data class TableColumn(
        val name: String,
        val type: String,
        /** The 1-based position of the column in the primary key, or 0 when it is not part of it. */
        val keyPosition: Int,
        val hidden: Int,
    )

    /** A column of a foreign key exactly as `PRAGMA foreign_key_list` reports it. */
    private data class ForeignKeyColumn(
        val id: Int,
        val position: Int,
        val targetTable: String,
        val columnName: String,
        val targetColumnName: String?,
        val updateRule: String,
        val deleteRule: String,
    )

    private fun tableColumns(connection: Connection, tableName: String): List<TableColumn> {
        return connection.query("SELECT name, type, pk, hidden FROM pragma_table_xinfo(?) ORDER BY cid", tableName) { rs ->
            buildList {
                while (rs.next()) {
                    add(TableColumn(rs.getString("name"), rs.getString("type").orEmpty(), rs.getInt("pk"), rs.getInt("hidden")))
                }
            }
        }
    }

    /** The name of the table that [reported] refers to, which may be spelled in another case, or `null` when there is none. */
    private fun tableName(connection: Connection, reported: String): String? {
        return connection.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ? COLLATE NOCASE", reported) { rs ->
            if (rs.next()) rs.getString("name") else null
        }
    }

    /** The `CREATE TABLE` statement of a table as it was written, or an empty string when there is none. */
    private fun tableDdl(connection: Connection, tableName: String): String {
        return connection.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ? COLLATE NOCASE", tableName) { rs ->
            if (rs.next()) rs.getString("sql").orEmpty() else ""
        }
    }

    /** The name among these that [reported] refers to, since identifiers are case-insensitive, or [reported] itself. */
    private fun List<String>.matching(reported: String): String = firstOrNull {
        it.equals(reported, ignoreCase = true)
    } ?: reported

    // Brings a declared type to the spelling the mapper matches on.
    // For example, `SINGLE` is what Exposed declares for `float()` specifically.
    // Every other type that the mapper knows is already spelled the standard way.
    private fun normalizeDeclaredType(declaredType: String): String {
        val normalized = declaredType.trim().uppercase().replace(WHITESPACE_PATTERN, " ")
        return if (normalized == EXPOSED_FLOAT_TYPE) "FLOAT" else normalized
    }

    /**
     * Cuts the expression of the generated column [columnName] out of the `CREATE TABLE` statement [ddl], which is the
     * text between the balanced parentheses that follow its `AS` keyword.
     * Returns `null` when it cannot be found.
     */
    private fun generationExpression(ddl: String, columnName: String): String? {
        val name = Regex.escape(columnName)
        val definition = Regex(
            """(?<![\w"`\[])["`\[]?$name["`\]]?\s+[^,()]*(?:\([^()]*\)[^,()]*)*?\bAS\s*\(""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(ddl) ?: return null

        val start = definition.range.last + 1
        var depth = 1
        var inString = false
        for (index in start until ddl.length) {
            val char = ddl[index]
            when {
                char == '\'' -> inString = !inString
                inString -> Unit
                char == '(' -> depth++
                char == ')' -> depth--
            }
            if (depth == 0) return ddl.substring(start, index).trim()
        }
        return null
    }

    private fun ruleCode(rule: String): Int = when (rule.uppercase()) {
        "CASCADE" -> DatabaseMetaData.importedKeyCascade
        "RESTRICT" -> DatabaseMetaData.importedKeyRestrict
        "SET NULL" -> DatabaseMetaData.importedKeySetNull
        "SET DEFAULT" -> DatabaseMetaData.importedKeySetDefault
        else -> DatabaseMetaData.importedKeyNoAction
    }

    private const val VIRTUAL_COLUMN: Int = 2
    private const val STORED_COLUMN: Int = 3
    private const val ROWID_ALIAS_TYPE: String = "INTEGER"
    private const val EXPOSED_FLOAT_TYPE: String = "SINGLE"

    private val WHITESPACE_PATTERN: Regex = Regex("""\s+""")

    /** Matches the table option that gives a table no rowid, so that an `INTEGER` primary key is an ordinary column. */
    private val WITHOUT_ROWID_PATTERN: Regex = Regex("""\)[^)]*\bWITHOUT\s+ROWID\b[^)]*$""", RegexOption.IGNORE_CASE)

    private inline fun <T> Connection.query(sql: String, vararg parameters: String, read: (ResultSet) -> T): T =
        prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, parameter -> statement.setString(index + 1, parameter) }
            statement.executeQuery().use(read)
        }
}
