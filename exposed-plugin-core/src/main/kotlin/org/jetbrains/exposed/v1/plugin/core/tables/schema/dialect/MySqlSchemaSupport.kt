package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import java.sql.Connection

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

    override fun isSystemSchema(connection: Connection, name: String): Boolean {
        return name.lowercase() in MySqlFamilyCatalog.SYSTEM_DATABASES
    }

    override fun declaredColumnTypes(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, String> {
        return MySqlFamilyCatalog.columns(connection, schema, tableName)
            .associate { it.name to normalizeDeclaredType(it.columnType) }
    }

    // `EXTRA` is `VIRTUAL GENERATED` or `STORED GENERATED` for a generated column, and `DEFAULT_GENERATED` for a column
    // that merely has an expression as its default, which the driver reports as a generated column as well
    override fun generatedColumns(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return MySqlFamilyCatalog.columns(connection, schema, tableName)
            .filter { it.isGenerated }
            .associate { it.name to it.generationExpression.removeSurrounding("(", ")") }
    }

    override fun uniqueConstraintNames(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return MySqlFamilyCatalog.uniqueConstraintNames(connection, schema, tableName)
    }

    override fun sequenceParameters(
        connection: Connection,
        schema: String,
        sequenceName: String,
        sequenceMaxValue: Long
    ): SequenceParameters? = null

    override fun ownedSequences(connection: Connection, schema: String, tableName: String): Map<String, String> = emptyMap()

    // MySQL has no sequences, so there is nothing to scope
    override fun sequences(connection: Connection, schemas: List<String>): List<SequenceReference>? = null

    // Enumeration values are directly in the column type, see [MySqlSyntax.enumerationValues]
    override fun enumerationTypes(connection: Connection, schema: String): Map<String, List<String>> = emptyMap()

    // Read from the catalog rather than from the driver, which reports `RESTRICT` both for a rule that was declared as
    // such and for one that was not declared at all. The catalog reports the latter as `NO ACTION`, which is also what
    // Exposed's migration utilities compare a key's rules against
    override fun foreignKeys(connection: Connection, schema: String, tableName: String): List<ReportedForeignKeyColumn> {
        return MySqlFamilyCatalog.foreignKeys(connection, schema, tableName)
    }

    override fun columnOverrides(connection: Connection, schema: String, tableName: String): Map<String, ColumnOverride> {
        return MySqlFamilyCatalog.onUpdateOverrides(MySqlFamilyCatalog.columns(connection, schema, tableName), tableName)
    }

    // A default is reported without quotes whatever it is, so a string cannot be told from a function by its text.
    // `EXTRA` says `DEFAULT_GENERATED` for an expression, for example. A literal of a character, enumeration, or date-time
    // column is given back its quotes, and an expression is given back the spelling it was most likely declared with.
    override fun columnDefaults(connection: Connection, schema: String, tableName: String): Map<String, String?> {
        return MySqlFamilyCatalog.columns(connection, schema, tableName).associate { column ->
            val default = column.default
            column.name to when {
                default == null -> null
                column.extra.contains(EXPRESSION_DEFAULT_MARKER, ignoreCase = true) -> normalizeDefaultExpression(default)
                column.dataType.lowercase() in QUOTED_LITERAL_TYPES -> "'${default.replace("'", "''")}'"
                else -> default
            }
        }
    }

    override fun indexFilters(connection: Connection, schema: String, tableName: String): Map<String, String> = emptyMap()

    override fun indexExpressions(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, List<String>> = emptyMap()

    override fun unsupportedTableNote(connection: Connection, schema: String, tableName: String): String? = null

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

    /** The types whose literal default is a string in SQL. */
    private val QUOTED_LITERAL_TYPES: Set<String> = setOf(
        "char", "varchar", "tinytext", "text", "mediumtext", "longtext", "enum", "set", "date", "time", "datetime", "timestamp",
    )

    private const val EXPRESSION_DEFAULT_MARKER: String = "DEFAULT_GENERATED"

    private val TIMESTAMP_TYPE_PATTERN: Regex = Regex("""TIMESTAMP(?:\(\d+\))?""")
    private val CURRENT_TIMESTAMP_PATTERN: Regex = Regex("""(?:CURRENT_TIMESTAMP|NOW)(?:\(\d*\))?""", RegexOption.IGNORE_CASE)
}
