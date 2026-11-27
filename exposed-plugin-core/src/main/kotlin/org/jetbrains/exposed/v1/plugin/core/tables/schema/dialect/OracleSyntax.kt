package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

/**
 * Information specific for exactly how to interpret strings and codes reported by an Oracle database.
 */
internal object OracleSyntax : DialectSyntax {
    // The driver reports every number as NUMERIC and a DATE as TIMESTAMP, so a type is never guessed from it
    override val usesJdbcTypeFallback: Boolean
        get() = false

    // Exposed declares column comments on Oracle and compares them during migrations
    override val generatesColumnComments: Boolean
        get() = true

    // Types that the mapper knows from other databases, but that no Exposed function declares exactly here:
    // - FLOAT with any other precision than 126, which is what both `float()` and `double()` declare
    // - NCLOB, while `text()` declares CLOB
    // - BOOLEAN, while `bool()` declares CHAR(1)
    // - JSON, while `json()` declares VARCHAR2(4000)
    // - TIMESTAMP WITH LOCAL TIME ZONE, which no function declares
    // - TIMESTAMP, with or without a time zone, at any other precision than the default of 6 fractional digits
    @Suppress("ForEachOnRange")
    override val unmappedTypes: Set<String> = buildSet {
        addAll(listOf("FLOAT", "NCLOB", "BOOLEAN", "JSON", "TIMESTAMP WITH LOCAL TIME ZONE"))
        (0..MAX_FRACTIONAL_SECONDS_PRECISION).filter { it != DEFAULT_FRACTIONAL_SECONDS_PRECISION }.forEach {
            add("TIMESTAMP($it)")
            add("TIMESTAMP($it) WITH TIME ZONE")
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override fun closestFunction(declaredType: String): String? {
        val type = declaredType.trim().uppercase()
        val arguments = type.substringAfter('(', "").substringBefore(')').split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            type.endsWith("WITH LOCAL TIME ZONE") -> "timestamp(name)"
            type.endsWith("WITH TIME ZONE") -> "timestampWithTimeZone(name)"
            type.startsWith("TIMESTAMP") -> "datetime(name)"
            // a number without a precision holds up to 38 digits
            type.startsWith("NUMBER") -> when {
                arguments.size != 2 -> null
                arguments[0] == "*" -> arguments[1].toIntOrNull()
                    ?.takeIf { it >= 0 }
                    ?.let { "decimal(name, $MAX_NUMBER_PRECISION, $it)" }
                else -> "decimal(name, ${arguments[0]}, 0)"
            }
            type.startsWith("FLOAT") || type == "BINARY_DOUBLE" -> "double(name)"
            type == "BINARY_FLOAT" -> "float(name)"
            type.startsWith("NCHAR") -> arguments.firstOrNull()?.let { "char(name, $it)" }
            type.startsWith("NVARCHAR2") -> arguments.firstOrNull()?.let { "varchar(name, $it)" }
            type == "NCLOB" || type == "LONG" -> "text(name)"
            type.startsWith("RAW") || type == "LONG RAW" -> "blob(name)"
            type == "BOOLEAN" -> "bool(name)"
            type == "JSON" -> "json(name, serialize, deserialize)"
            type.startsWith("VECTOR") -> "vector(name, ${arguments.firstOrNull()?.toIntOrNull() ?: "null"})"
            else -> null
        }
    }

    // A sequence default is stored as `"SCHEMA"."SEQUENCE"."NEXTVAL"`, with the schema always added by the database.
    // The sequence that the database keeps for an identity column is no default of this kind
    override fun sequenceInDefault(defaultValue: String): SequenceReference? {
        val match = NEXT_VALUE_PATTERN.matchEntire(defaultValue.trim()) ?: return null
        val name = match.groupValues[2].unquoted()
        if (isIdentitySequenceName(name)) return null
        return SequenceReference(schema = match.groupValues[1].unquoted().ifEmpty { null }, name = name)
    }

    // An unnamed constraint of any kind is named `SYS_C` followed by a number, as in `SYS_C008740`
    override fun isGeneratedConstraintName(name: String, tableName: String, columnNames: List<String>): Boolean {
        return GENERATED_CONSTRAINT_NAME_PATTERN.matches(name)
    }

    /** Whether [name] is what the database names the sequence behind an identity column, as in `ISEQ$$_71701`. */
    fun isIdentitySequenceName(name: String): Boolean = name.startsWith(IDENTITY_SEQUENCE_PREFIX)

    private fun String.unquoted(): String = if (length >= 2 && startsWith('"') && endsWith('"')) substring(1, length - 1) else this

    private const val DEFAULT_FRACTIONAL_SECONDS_PRECISION: Int = 6
    private const val MAX_FRACTIONAL_SECONDS_PRECISION: Int = 9
    private const val MAX_NUMBER_PRECISION: Int = 38
    private const val IDENTITY_SEQUENCE_PREFIX: String = "ISEQ\$\$_"

    /** Matches `[schema.]sequence.NEXTVAL`, with each part optionally in double quotes. */
    private val NEXT_VALUE_PATTERN: Regex = Regex(
        """^(?:("[^"]+"|[A-Za-z0-9_$#]+)\s*\.\s*)?("[^"]+"|[A-Za-z0-9_$#]+)\s*\.\s*"?NEXTVAL"?$""",
        RegexOption.IGNORE_CASE
    )

    private val GENERATED_CONSTRAINT_NAME_PATTERN: Regex = Regex("""^SYS_C\d+$""")
}
