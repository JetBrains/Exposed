package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.MySqlFamilyCatalog.query
import java.sql.Connection

/**
 * Responsible for obtaining non-standard metadata from a MariaDB database.
 *
 * As on MySQL, a schema is a database, which JDBC reports as a catalog, and the driver's own column metadata loses the
 * declared precision of a type. Unlike MySQL, the driver reports a default as it must be written, so that a string can
 * be told from a function, but it does not report every foreign key. So declared types and foreign keys are read from
 * `INFORMATION_SCHEMA`, and defaults are taken from the driver.
 */
internal object MariaDbSchemaSupport : DialectSchemaSupport {
    override val syntax: DialectSyntax = MariaDbSyntax

    // Every foreign key is backed with an index, named after the constraint or, for an unnamed key, after its first column
    override val createsIndexForForeignKeys: Boolean = true

    override val supportsSchemas: Boolean = true

    override val schemasAreCatalogs: Boolean = true

    override fun isSystemSchema(name: String): Boolean = name.lowercase() in MySqlFamilyCatalog.SYSTEM_DATABASES

    override fun declaredColumnTypes(
        connection: Connection,
        schema: String,
        tableName: String
    ): Map<String, String> {
        val jsonColumns = jsonColumns(connection, schema, tableName)
        return MySqlFamilyCatalog.columns(connection, schema, tableName).associate { column ->
            column.name to normalizeDeclaredType(column.columnType, isJson = column.name in jsonColumns)
        }
    }

    // `EXTRA` is `VIRTUAL GENERATED` or `STORED GENERATED` for a generated column. The expression is reported without
    // surrounding parentheses, so it is taken as it is
    override fun generatedColumns(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return MySqlFamilyCatalog.columns(connection, schema, tableName)
            .filter { it.isGenerated }
            .associate { it.name to it.generationExpression }
    }

    override fun uniqueConstraintNames(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return MySqlFamilyCatalog.uniqueConstraintNames(connection, schema, tableName)
    }

    // A sequence is a table of a single row that holds its own parameters, including the cache size, which
    // `INFORMATION_SCHEMA.SEQUENCES` leaves out
    override fun sequenceParameters(
        connection: Connection,
        schema: String,
        sequenceName: String,
        sequenceMaxValue: Long
    ): SequenceParameters? {
        return connection.query(
            "SELECT start_value, minimum_value, maximum_value, increment, cache_size, cycle_option " +
                "FROM ${schema.quoted()}.${sequenceName.quoted()}"
        ) { rs ->
            if (!rs.next()) return@query null
            val maximum = rs.getLong("maximum_value")
            SequenceParameters(
                startWith = rs.getLong("start_value").takeIf { it != IMPLIED_SEQUENCE_START },
                incrementBy = rs.getLong("increment").takeIf { it != DEFAULT_SEQUENCE_INCREMENT },
                minValue = rs.getLong("minimum_value").takeIf { it != IMPLIED_SEQUENCE_MINIMUM },
                // the highest value a sequence can take is one below what Exposed asks for, so the server stores that instead
                maxValue = maximum.takeIf { it != sequenceMaxValue && it != sequenceMaxValue - 1 },
                cycle = true.takeIf { rs.getBoolean("cycle_option") },
                cache = rs.getLong("cache_size").takeIf { it != DEFAULT_SEQUENCE_CACHE },
            ).takeUnless { it.isDefault }
        }
    }

    // An auto-increment column never owns a separate sequence object
    override fun ownedSequences(connection: Connection, schema: String, tableName: String): Map<String, String> = emptyMap()

    // Exposed lists the sequences of every database of the server, under their bare names
    override fun sequences(connection: Connection, schemas: List<String>): List<SequenceReference> =
        schemas.flatMap { schema ->
            connection.query(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'SEQUENCE' ORDER BY TABLE_NAME",
                schema,
            ) { rs ->
                buildList {
                    while (rs.next()) {
                        add(SequenceReference(schema, rs.getString("TABLE_NAME")))
                    }
                }
            }
        }

    // Enumeration values are directly in the column type, see [MariaDbSyntax.enumerationValues]
    override fun enumerationTypes(connection: Connection, schema: String): Map<String, List<String>> = emptyMap()

    // Read from the catalog rather than from the driver, which was found to leave out every key that targets a primary
    // key. The catalog reports a rule that was not declared at all as `RESTRICT`, which is also what Exposed assumes
    // for such a rule, and what its migration utilities compare a key's rules against
    override fun foreignKeys(connection: Connection, schema: String, tableName: String): List<ReportedForeignKeyColumn> {
        return MySqlFamilyCatalog.foreignKeys(connection, schema, tableName)
    }

    override fun columnOverrides(connection: Connection, schema: String, tableName: String): Map<String, ColumnOverride> {
        return MySqlFamilyCatalog.onUpdateOverrides(MySqlFamilyCatalog.columns(connection, schema, tableName), tableName)
    }

    // The driver reports a default as it must be written: a string in quotes, a function by name. See
    // [MariaDbSyntax.normalizeDefault] for the spelling of functions
    override fun columnDefaults(connection: Connection, schema: String, tableName: String): Map<String, String?>? = null

    override fun indexFilters(connection: Connection, schema: String, tableName: String): Map<String, String> = emptyMap()

    // A system-versioned table is listed as an ordinary table by the driver
    override fun unsupportedTableNote(connection: Connection, schema: String, tableName: String): String? {
        val tableType = connection.query(
            "SELECT TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
            schema,
            tableName,
        ) { rs -> if (rs.next()) rs.getString("TABLE_TYPE") else null }
        if (!tableType.equals(SYSTEM_VERSIONED_TABLE_TYPE, ignoreCase = true)) return null
        return "Table '$tableName' is system-versioned (WITH SYSTEM VERSIONING), which the generated definition does not declare. " +
            "Statements made through the definition see and change current rows only, and row history cannot be queried through it. " +
            "If the intention is to create new schema from this definition, " +
            "override val options = listOf(Table.RawTableOption(\"WITH SYSTEM VERSIONING\")) should be manually added"
    }

    /**
     * Returns the names of the columns of a table that were declared as `JSON`. MariaDB stores such a column as
     * `LONGTEXT` and adds a column-level `CHECK` constraint named after the column, which is all that tells it from a
     * column declared as `LONGTEXT`. Exposed's `json()` creates exactly the same column.
     */
    private fun jsonColumns(connection: Connection, schema: String, tableName: String): Set<String> {
        return connection.query(
            "SELECT CONSTRAINT_NAME, CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS " +
                "WHERE CONSTRAINT_SCHEMA = ? AND TABLE_NAME = ? AND LEVEL = 'Column'",
            schema,
            tableName,
        ) { rs ->
            buildSet {
                while (rs.next()) {
                    val name = rs.getString("CONSTRAINT_NAME")
                    val clause = rs.getString("CHECK_CLAUSE").orEmpty().trim()
                    if (clause.equals("json_valid(${name.quoted()})", ignoreCase = true)) add(name)
                }
            }
        }
    }

    private fun normalizeDeclaredType(columnType: String, isJson: Boolean): String {
        val declared = columnType.trim()
        if (declared.startsWith("enum(", ignoreCase = true)) return declared
        val normalized = declared.uppercase()
        return when {
            isJson && normalized == "LONGTEXT" -> "JSON"
            normalized == "TINYINT(1)" -> "BOOLEAN"
            // Unlike on MySQL, Exposed declares its `timestamp()` as `TIMESTAMP` here, and its `datetime()` as `DATETIME`.
            // The mapper already reads a plain `TIMESTAMP` as `datetime()`, which is what it is on other databases, so the
            // type is given the name that says what it is on MariaDB: a point in time, stored in UTC and shown in the
            // time zone of the session
            TIMESTAMP_TYPE_PATTERN.matches(normalized) -> "TIMESTAMP WITH LOCAL TIME ZONE"
            // an integer type is reported with a display width, `int(11)` or `bigint(20) unsigned`, which is not part of the type
            else -> DISPLAY_WIDTH_PATTERN.replace(normalized, "$1")
        }
    }

    /** Quotes this identifier for use in a statement, where it cannot be a parameter. */
    private fun String.quoted(): String = "`${replace("`", "``")}`"

    private const val SYSTEM_VERSIONED_TABLE_TYPE: String = "SYSTEM VERSIONED"

    /** What `autoIncrement("sequence")` declares, or leaves to the database, when it creates a sequence. */
    private const val IMPLIED_SEQUENCE_START: Long = 1
    private const val IMPLIED_SEQUENCE_MINIMUM: Long = 1
    private const val DEFAULT_SEQUENCE_INCREMENT: Long = 1
    private const val DEFAULT_SEQUENCE_CACHE: Long = 1000

    private val TIMESTAMP_TYPE_PATTERN: Regex = Regex("""TIMESTAMP(?:\(\d+\))?""")

    /** Matches the leading integer type, or `YEAR`, of a declared type, with the display width that follows it. */
    private val DISPLAY_WIDTH_PATTERN: Regex = Regex("""^(TINYINT|SMALLINT|MEDIUMINT|INT|BIGINT|YEAR)\(\d+\)""")
}
