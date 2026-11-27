package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

/**
 * Information specific for exactly how to interpret strings and codes reported by a PostgreSQL database.
 */
internal object PostgreSQLSyntax : DialectSyntax {
    override val unboundedLength: Int
        get() = Int.MAX_VALUE

    // an array is `<element>[]`, once per dimension with no stored maximum cardinality
    override fun arrayElement(declaredType: String): ArrayElement? {
        val match = ARRAY_TYPE_PATTERN.matchEntire(declaredType) ?: return null
        return ArrayElement(
            match.groupValues[1],
            maximumCardinality = null,
            dimensions = match.groupValues[2].length / ARRAY_SUFFIX.length
        )
    }

    // a sequence default is `nextval('seq'::regclass)`, with the name possibly schema-qualified and either part possibly quoted
    override fun sequenceInDefault(defaultValue: String): SequenceReference? {
        val match = NEXTVAL_PATTERN.matchEntire(defaultValue.trim()) ?: return null
        return SequenceReference(schema = match.groupValues[1].ifEmpty { null }, name = match.groupValues[2])
    }

    // Unnamed constraints are patterned after the table and its columns: `<table>_pkey`, `<table>_<columns>_key`
    // for a unique constraint and `<table>_<columns>_fkey` for a foreign key, with a number appended when the name is taken
    override fun isGeneratedConstraintName(name: String, tableName: String, columnNames: List<String>): Boolean {
        val columns = columnNames.joinToString("_")
        val schemes = listOf("${tableName}_pkey", "${tableName}_${columns}_key", "${tableName}_${columns}_fkey")
        return schemes.any { scheme ->
            name == scheme || (name.startsWith(scheme) && name.substring(scheme.length).all(Char::isDigit))
        }
    }

    // A default is reported with a cast to the column's type, `'hello'::text` or `'2024-01-31'::date`.
    // Function defaults (`CURRENT_TIMESTAMP`, `now()`, `gen_random_uuid()`) are left as reported.
    override fun normalizeDefault(reported: String): String {
        val match = CAST_PATTERN.matchEntire(reported.trim()) ?: return reported
        val (value, castType) = match.destructured
        val baseCastType = castType.substringBefore('(').trim().lowercase()
        val isQuoted = value.length > 1 && value.startsWith('\'') && value.endsWith('\'')
        return when {
            isQuoted && baseCastType in TYPED_LITERAL_PREFIXES -> "${TYPED_LITERAL_PREFIXES.getValue(baseCastType)} $value"
            isQuoted && baseCastType in NUMERIC_CAST_TYPES -> value.substring(1, value.length - 1)
            else -> value
        }
    }

    private const val ARRAY_SUFFIX: String = "[]"

    /** Matches the element type and the `[]` suffixes of an array type. */
    private val ARRAY_TYPE_PATTERN: Regex = Regex("""^(.+?)((?:\[])+)$""")

    /** Matches `nextval('[schema.]sequence'::regclass)` with either part optionally double-quoted inside the string. */
    private val NEXTVAL_PATTERN: Regex = Regex("""^nextval\('(?:"?([^".']+)"?\.)?"?([^"']+)"?'(?:::regclass)?\)$""", RegexOption.IGNORE_CASE)

    /** Matches `<value>::<type>` where the type may carry a precision and may be an array type. */
    private val CAST_PATTERN: Regex = Regex("""^(.+?)::([a-z_ ]+(?:\(\d+(?:,\s*\d+)?\))?(?:\[])*)$""", RegexOption.IGNORE_CASE)

    private val TYPED_LITERAL_PREFIXES: Map<String, String> = mapOf(
        "date" to "DATE",
        "time without time zone" to "TIME",
        "time" to "TIME",
        "timestamp without time zone" to "TIMESTAMP",
        "timestamp" to "TIMESTAMP",
    )

    private val NUMERIC_CAST_TYPES: Set<String> = setOf("smallint", "integer", "bigint", "numeric", "real", "double precision")
}
