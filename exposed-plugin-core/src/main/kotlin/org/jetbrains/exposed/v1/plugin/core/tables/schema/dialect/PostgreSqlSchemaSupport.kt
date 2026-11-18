package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.use

/**
 * Responsible for obtaining non-standard metadata from a PostgreSQL database.
 */
internal object PostgreSqlSchemaSupport : DialectSchemaSupport {
    override val syntax: DialectSyntax = PostgreSQLSyntax

    override val createsIndexForForeignKeys: Boolean = false
    override fun isSystemSchema(name: String): Boolean = name == "information_schema" ||
        name == "pg_catalog" ||
        name.startsWith("pg_toast") ||
        name.startsWith("pg_temp_")

    override fun declaredColumnTypes(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return connection.query(
            "SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS declared, a.attndims " +
                "FROM pg_catalog.pg_attribute a " +
                "JOIN pg_catalog.pg_class t ON a.attrelid = t.oid " +
                "JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "WHERE n.nspname = ? AND t.relname = ? AND a.attnum > 0 AND NOT a.attisdropped",
            schema,
            tableName,
        ) { rs ->
            buildMap {
                while (rs.next()) {
                    put(rs.getString("attname"), normalizeDeclaredType(rs.getString("declared"), rs.getInt("attndims")))
                }
            }
        }
    }

    override fun generatedColumns(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return connection.query(
            "SELECT column_name, generation_expression FROM information_schema.columns " +
                "WHERE table_schema = ? AND table_name = ? AND is_generated = 'ALWAYS'",
            schema,
            tableName,
        ) { rs ->
            buildMap {
                while (rs.next()) {
                    put(rs.getString("column_name"), rs.getString("generation_expression").orEmpty())
                }
            }
        }
    }

    // An index that backs a unique constraint carries the constraint's own name, so every entry maps a name to itself.
    // The map can still distinguish a constraint-backed index (whose generated `<table>_<columns>_key` name is omitted)
    // from a unique index created under that name on purpose
    override fun uniqueConstraintNames(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return connection.query(
            "SELECT c.conname, i.relname AS indexname " +
                "FROM pg_catalog.pg_constraint c " +
                "JOIN pg_catalog.pg_class t ON c.conrelid = t.oid " +
                "JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "JOIN pg_catalog.pg_class i ON c.conindid = i.oid " +
                "WHERE c.contype = 'u' AND n.nspname = ? AND t.relname = ?",
            schema,
            tableName,
        ) { rs ->
            buildMap {
                while (rs.next()) {
                    put(rs.getString("indexname"), rs.getString("conname"))
                }
            }
        }
    }

    override fun sequenceParameters(
        connection: Connection,
        schema: String,
        sequenceName: String,
        sequenceMaxValue: Long
    ): SequenceParameters? {
        return connection.query(
            "SELECT s.seqstart, s.seqincrement, s.seqmin, s.seqmax, s.seqcycle, s.seqcache " +
                "FROM pg_catalog.pg_sequence s " +
                "JOIN pg_catalog.pg_class c ON s.seqrelid = c.oid " +
                "JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid " +
                "WHERE n.nspname = ? AND c.relname = ?",
            schema,
            sequenceName,
        ) { rs ->
            if (!rs.next()) return@query null
            SequenceParameters(
                startWith = rs.getLong("seqstart").takeIf { it != IMPLIED_SEQUENCE_START },
                incrementBy = rs.getLong("seqincrement").takeIf { it != DEFAULT_SEQUENCE_INCREMENT },
                minValue = rs.getLong("seqmin").takeIf { it != IMPLIED_SEQUENCE_MINIMUM },
                maxValue = rs.getLong("seqmax").takeIf { it != sequenceMaxValue },
                cycle = true.takeIf { rs.getBoolean("seqcycle") },
                cache = rs.getLong("seqcache").takeIf { it != DEFAULT_SEQUENCE_CACHE },
            ).takeUnless { it.isDefault }
        }
    }

    // A `SERIAL` column owns its sequence through an `auto` dependency, while an identity column owns it
    // through an `internal` one. Both are considered ordinary auto-increment columns, so their sequences must
    // not be reported as unused.
    override fun ownedSequences(connection: Connection, schema: String, tableName: String): Map<String, String> {
        return connection.query(
            "SELECT a.attname, s.relname AS seqname " +
                "FROM pg_catalog.pg_depend d " +
                "JOIN pg_catalog.pg_class s ON d.objid = s.oid AND s.relkind = 'S' " +
                "JOIN pg_catalog.pg_class t ON d.refobjid = t.oid " +
                "JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "JOIN pg_catalog.pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid " +
                "WHERE d.deptype IN ('a', 'i') AND n.nspname = ? AND t.relname = ?",
            schema,
            tableName,
        ) { rs ->
            buildMap {
                while (rs.next()) {
                    put(rs.getString("attname"), rs.getString("seqname"))
                }
            }
        }
    }

    // Every enumeration type of the database, keyed both by its bare name and by its schema-qualified name.
    // `format_type()` qualifies a type only when its schema is not on the connection's search path.
    // Values are in declaration order
    override fun enumerationTypes(connection: Connection, schema: String): Map<String, List<String>> {
        return connection.query(
            "SELECT n.nspname, t.typname, e.enumlabel " +
                "FROM pg_catalog.pg_enum e " +
                "JOIN pg_catalog.pg_type t ON e.enumtypid = t.oid " +
                "JOIN pg_catalog.pg_namespace n ON t.typnamespace = n.oid " +
                "ORDER BY n.nspname, t.typname, e.enumsortorder",
        ) { rs ->
            val valuesByQualifiedName = linkedMapOf<Pair<String, String>, MutableList<String>>()
            while (rs.next()) {
                valuesByQualifiedName.getOrPut(rs.getString("nspname") to rs.getString("typname")) { mutableListOf() }
                    .add(rs.getString("enumlabel"))
            }
            buildMap {
                valuesByQualifiedName.forEach { (qualifiedName, values) ->
                    val (typeSchema, typeName) = qualifiedName
                    put(typeName, values)
                    put("$typeSchema.$typeName", values)
                }
            }
        }
    }

    private fun normalizeDeclaredType(formatType: String, dimensions: Int): String {
        val baseType = formatType.removeSuffix("[]").trim()
        val normalized = TYPE_NAME_PATTERNS.firstNotNullOfOrNull { (pattern, replacement) ->
            pattern.matchEntire(baseType)?.let { match -> replacement(match) }
        } ?: baseType
        return normalized + "[]".repeat(dimensions.coerceAtLeast(0))
    }

    private val TYPE_NAME_PATTERNS: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("""character varying\((\d+)\)""") to { "VARCHAR(${it.groupValues[1]})" },
        Regex("""character varying""") to { "VARCHAR" },
        Regex("""character\((\d+)\)""") to { "CHAR(${it.groupValues[1]})" },
        Regex("""character""") to { "CHAR" },
        Regex("""numeric\((\d+),(\d+)\)""") to { "NUMERIC(${it.groupValues[1]}, ${it.groupValues[2]})" },
        Regex("""numeric""") to { "NUMERIC" },
        Regex("""timestamp(?:\(\d+\))? without time zone""") to { "TIMESTAMP" },
        Regex("""timestamp(?:\(\d+\))? with time zone""") to { "TIMESTAMP WITH TIME ZONE" },
        Regex("""time(?:\(\d+\))? without time zone""") to { "TIME" },
        Regex("""time(?:\(\d+\))? with time zone""") to { "TIME WITH TIME ZONE" },
        Regex("""double precision""") to { "DOUBLE PRECISION" },
        Regex("""bytea""") to { "VARBINARY" },
        Regex("""(?:\w+\.)?vector\((\d+)\)""") to { "VECTOR(${it.groupValues[1]})" },
        Regex("""(?:\w+\.)?vector""") to { "VECTOR" },
        Regex("""smallint|integer|bigint|real|boolean|text|uuid|date|json|jsonb""") to { it.value.uppercase() },
    )

    private const val IMPLIED_SEQUENCE_START: Long = 1
    private const val IMPLIED_SEQUENCE_MINIMUM: Long = 1
    private const val DEFAULT_SEQUENCE_INCREMENT: Long = 1
    private const val DEFAULT_SEQUENCE_CACHE: Long = 1

    private inline fun <T> Connection.query(sql: String, vararg parameters: String, read: (ResultSet) -> T): T =
        prepareStatement(sql).use { statement: PreparedStatement ->
            parameters.forEachIndexed { index, parameter -> statement.setString(index + 1, parameter) }
            statement.executeQuery().use(read)
        }
}
