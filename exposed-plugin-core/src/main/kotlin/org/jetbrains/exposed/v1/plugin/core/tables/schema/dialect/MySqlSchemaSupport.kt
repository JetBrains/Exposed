package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet

/**
 * Responsible for obtaining non-standard metadata from a MySQL database.
 *
 * A MySQL schema is a database, which JDBC reports as a catalog. The driver's own column metadata loses the declared
 * precision of a type, reports a default without saying whether it is a string or an expression, and does not tell an
 * undeclared foreign key rule from `RESTRICT`. So all of these are read from `INFORMATION_SCHEMA` instead.
 */
internal object MySqlSchemaSupport : DialectSchemaSupport {
    override val syntax: DialectSyntax = MySqlSyntax

    // Every foreign key is backed with an index, named after the constraint or, for an unnamed key, after its first column
    override val createsIndexForForeignKeys: Boolean = true

    override val supportsSchemas: Boolean = true

    override val schemasAreCatalogs: Boolean = true

    override fun isSystemSchema(name: String): Boolean = name.lowercase() in SYSTEM_DATABASES

    override fun declaredColumnTypes(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, String> {
        return columns(connection, schema, tableName).associate { it.name to normalizeDeclaredType(it.columnType) }
    }

    // `EXTRA` is `VIRTUAL GENERATED` or `STORED GENERATED` for a generated column, and `DEFAULT_GENERATED` for a column
    // that merely has an expression as its default, which the driver reports as a generated column as well
    override fun generatedColumns(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return columns(connection, schema, tableName)
            .filter { it.isGenerated }
            .associate { it.name to it.generationExpression.removeSurrounding("(", ")") }
    }

    // A `UNIQUE` constraint and the index that backs it share one name
    override fun uniqueConstraintNames(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return connection.query(
            "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'UNIQUE'",
            schema,
            tableName,
        ) { rs ->
            buildMap {
                while (rs.next()) {
                    put(rs.getString("CONSTRAINT_NAME"), rs.getString("CONSTRAINT_NAME"))
                }
            }
        }
    }

    override fun sequenceParameters(
        connection: Connection,
        schema: String,
        sequenceName: String,
        sequenceMaxValue: Long
    ): SequenceParameters? = null

    override fun ownedSequences(connection: Connection, schema: String, tableName: String): Map<String, String> = emptyMap()

    // Enumeration values are directly in the column type, see [MySqlSyntax.enumerationValues]
    override fun enumerationTypes(connection: Connection, schema: String): Map<String, List<String>> = emptyMap()

    // Read from the catalog rather than from the driver, which reports `RESTRICT` both for a rule that was declared as
    // such and for one that was not declared at all. The catalog reports the latter as `NO ACTION`, which is also what
    // Exposed's migration utilities compare a key's rules against
    override fun foreignKeys(connection: Connection, schema: String, tableName: String): List<ReportedForeignKeyColumn> {
        return connection.query(
            "SELECT k.CONSTRAINT_NAME, k.ORDINAL_POSITION, k.COLUMN_NAME, k.REFERENCED_TABLE_SCHEMA, k.REFERENCED_TABLE_NAME, " +
                "k.REFERENCED_COLUMN_NAME, r.UPDATE_RULE, r.DELETE_RULE " +
                "FROM information_schema.KEY_COLUMN_USAGE k " +
                "JOIN information_schema.REFERENTIAL_CONSTRAINTS r " +
                "ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME AND r.TABLE_NAME = k.TABLE_NAME " +
                "WHERE k.TABLE_SCHEMA = ? AND k.TABLE_NAME = ? AND k.REFERENCED_TABLE_NAME IS NOT NULL " +
                "ORDER BY k.CONSTRAINT_NAME, k.ORDINAL_POSITION",
            schema,
            tableName,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    val name = rs.getString("CONSTRAINT_NAME")
                    add(
                        ReportedForeignKeyColumn(
                            groupKey = name,
                            name = name,
                            position = rs.getInt("ORDINAL_POSITION"),
                            columnName = rs.getString("COLUMN_NAME"),
                            targetSchema = rs.getString("REFERENCED_TABLE_SCHEMA"),
                            targetTable = rs.getString("REFERENCED_TABLE_NAME"),
                            targetColumnName = rs.getString("REFERENCED_COLUMN_NAME"),
                            deleteRule = ruleCode(rs.getString("DELETE_RULE")),
                            updateRule = ruleCode(rs.getString("UPDATE_RULE")),
                        )
                    )
                }
            }
        }
    }

    override fun columnOverrides(connection: Connection, schema: String, tableName: String): Map<String, ColumnOverride> =
        columns(connection, schema, tableName)
            .filter { it.extra.contains(ON_UPDATE_CLAUSE, ignoreCase = true) }
            .associate { column ->
                val clause = column.extra.substring(column.extra.indexOf(ON_UPDATE_CLAUSE, ignoreCase = true))
                column.name to ColumnOverride(
                    unsupportedNote = "Column '${column.name}' of table '$tableName' is declared with ${clause.uppercase()}, which the " +
                        "generated definition does not declare. If the intention is to create new schema from this definition, " +
                        "withDefinition(\"${clause.uppercase()}\") should be manually added, with the fractional seconds precision of " +
                        "the column type that Exposed declares"
                )
            }

    // A default is reported without quotes whatever it is, so a string cannot be told from a function by its text.
    // `EXTRA` says `DEFAULT_GENERATED` for an expression, for example. A literal of a character, enumeration, or date-time
    // column is given back its quotes, and an expression is given back the spelling it was most likely declared with.
    override fun columnDefaults(connection: Connection, schema: String, tableName: String): Map<String, String?> =
        columns(connection, schema, tableName).associate { column ->
            val default = column.default
            column.name to when {
                default == null -> null
                column.extra.contains(EXPRESSION_DEFAULT_MARKER, ignoreCase = true) -> normalizeDefaultExpression(default)
                column.dataType.lowercase() in QUOTED_LITERAL_TYPES -> "'${default.replace("'", "''")}'"
                else -> default
            }
        }

    /** A column of a table exactly as `INFORMATION_SCHEMA.COLUMNS` reports it. */
    private data class Column(
        val name: String,
        val columnType: String,
        val dataType: String,
        val default: String?,
        val extra: String,
        val generationExpression: String,
    ) {
        val isGenerated: Boolean
            get() = extra.contains("VIRTUAL GENERATED", ignoreCase = true) || extra.contains("STORED GENERATED", ignoreCase = true)
    }

    private fun columns(connection: Connection, schema: String, tableName: String): List<Column> {
        return connection.query(
            "SELECT COLUMN_NAME, COLUMN_TYPE, DATA_TYPE, COLUMN_DEFAULT, EXTRA, GENERATION_EXPRESSION " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
            schema,
            tableName,
        ) { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        Column(
                            name = rs.getString("COLUMN_NAME"),
                            columnType = rs.getString("COLUMN_TYPE"),
                            dataType = rs.getString("DATA_TYPE"),
                            default = rs.getString("COLUMN_DEFAULT"),
                            extra = rs.getString("EXTRA").orEmpty(),
                            generationExpression = rs.getString("GENERATION_EXPRESSION").orEmpty(),
                        )
                    )
                }
            }
        }
    }

    private fun normalizeDeclaredType(columnType: String): String {
        val declared = columnType.trim()
        if (declared.startsWith("enum(", ignoreCase = true)) return declared
        val normalized = declared.uppercase()
        return when {
            normalized == "TINYINT(1)" -> "BOOLEAN"
            TIMESTAMP_TYPE_PATTERN.matches(normalized) -> "TIMESTAMP WITH TIME ZONE"
            else -> normalized
        }
    }

    private fun normalizeDefaultExpression(default: String): String = when {
        default.equals("curdate()", ignoreCase = true) -> "CURRENT_DATE"
        CURRENT_TIMESTAMP_PATTERN.matches(default) -> "CURRENT_TIMESTAMP"
        else -> default
    }

    private fun ruleCode(rule: String): Int = when (rule.uppercase()) {
        "CASCADE" -> DatabaseMetaData.importedKeyCascade
        "RESTRICT" -> DatabaseMetaData.importedKeyRestrict
        "SET NULL" -> DatabaseMetaData.importedKeySetNull
        "SET DEFAULT" -> DatabaseMetaData.importedKeySetDefault
        else -> DatabaseMetaData.importedKeyNoAction
    }

    private val SYSTEM_DATABASES: Set<String> = setOf("information_schema", "mysql", "performance_schema", "sys")

    /** The types whose literal default is a string in SQL. */
    private val QUOTED_LITERAL_TYPES: Set<String> = setOf(
        "char", "varchar", "tinytext", "text", "mediumtext", "longtext", "enum", "set", "date", "time", "datetime", "timestamp",
    )

    private const val EXPRESSION_DEFAULT_MARKER: String = "DEFAULT_GENERATED"
    private const val ON_UPDATE_CLAUSE: String = "on update"

    private val TIMESTAMP_TYPE_PATTERN: Regex = Regex("""TIMESTAMP(?:\(\d+\))?""")
    private val CURRENT_TIMESTAMP_PATTERN: Regex = Regex("""(?:CURRENT_TIMESTAMP|NOW)(?:\(\d*\))?""", RegexOption.IGNORE_CASE)

    private inline fun <T> Connection.query(sql: String, vararg parameters: String, read: (ResultSet) -> T): T =
        prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, parameter -> statement.setString(index + 1, parameter) }
            statement.executeQuery().use(read)
        }
}
