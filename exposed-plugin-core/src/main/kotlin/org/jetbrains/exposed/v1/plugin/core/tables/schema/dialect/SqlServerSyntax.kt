package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

/**
 * Information specific for exactly how to interpret strings and codes reported by a SQL Server database.
 */
internal object SqlServerSyntax : DialectSyntax {
    // The driver reports a `uniqueidentifier` as CHAR and a `rowversion` as BINARY, so a type is never guessed from it
    override val usesJdbcTypeFallback: Boolean
        get() = false

    // Types that the mapper knows from other databases, but that no Exposed function declares exactly here:
    // - REAL is a 4-byte type, while `float()` declares FLOAT, which is 8 bytes
    // - DATETIME is the older date-time type, while `datetime()` declares DATETIME2
    // - BINARY is fixed-length, while `binary()` declares VARBINARY
    // - TEXT is the deprecated large character type, while `text()` declares VARCHAR(MAX)
    // - TIME with any other precision than the default, which is all that `time()` declares
    // - JSON and VECTOR, which neither the driver nor Exposed can read back from a table
    @Suppress("ForEachOnRange")
    override val unmappedTypes: Set<String> = buildSet {
        addAll(listOf("REAL", "DATETIME", "BINARY", "TEXT", "JSON", "VECTOR"))
        (0 until DEFAULT_FRACTIONAL_SECONDS_PRECISION).forEach { add("TIME($it)") }
    }

    @Suppress("CyclomaticComplexMethod")
    override fun closestFunction(declaredType: String): String? {
        val match = TYPE_PATTERN.matchEntire(declaredType.trim().uppercase()) ?: return null
        val (baseType, argument) = match.destructured
        return when (baseType) {
            "NVARCHAR" -> "varchar(name, $argument)"
            "NCHAR" -> "char(name, $argument)"
            "BINARY" -> "binary(name, $argument)"
            "REAL" -> "float(name)"
            "DATETIME", "SMALLDATETIME", "DATETIME2" -> "datetime(name)"
            "DATETIMEOFFSET" -> "timestampWithTimeZone(name)"
            "TIME" -> "time(name)"
            "TEXT", "NTEXT" -> "text(name)"
            "IMAGE" -> "blob(name)"
            "MONEY" -> "decimal(name, 19, 4)"
            "SMALLMONEY" -> "decimal(name, 10, 4)"
            "JSON" -> "json(name, serialize, deserialize)"
            "VECTOR" -> "vector(name, $argument)"
            else -> null
        }
    }

    // A sequence default is `NEXT VALUE FOR [schema].[sequence]`, with the schema only when it was declared with one
    override fun sequenceInDefault(defaultValue: String): SequenceReference? {
        val match = NEXT_VALUE_PATTERN.matchEntire(defaultValue.trim()) ?: return null
        return SequenceReference(
            schema = match.groupValues[1].unquoted().ifEmpty { null },
            name = match.groupValues[2].unquoted(),
        )
    }

    // An unnamed constraint is named after its kind, its table and, for a foreign key, its column, each cut short, with
    // a hexadecimal number at the end: `PK__orders__3213E83F4D1B48C9`, `UQ__orders__357D4CF9EDB2FF52` and
    // `FK__orders__user_i__5070F446`
    override fun isGeneratedConstraintName(name: String, tableName: String, columnNames: List<String>): Boolean {
        return GENERATED_KEY_NAME_PATTERN.matches(name) || GENERATED_FOREIGN_KEY_NAME_PATTERN.matches(name)
    }

    private fun String.unquoted(): String = when {
        startsWith('[') && endsWith(']') -> substring(1, length - 1).replace("]]", "]")
        startsWith('"') && endsWith('"') -> substring(1, length - 1).replace("\"\"", "\"")
        else -> this
    }

    private const val DEFAULT_FRACTIONAL_SECONDS_PRECISION: Int = 7

    /** Matches a normalized type, capturing its base name and its first argument, if any. */
    private val TYPE_PATTERN: Regex = Regex("""^([A-Z_][A-Z_0-9]*?)(?:\((\d+)(?:,\s*\d+)?\))?$""")

    /** Matches `NEXT VALUE FOR [schema.]sequence`, with either part optionally in brackets or double quotes. */
    private val NEXT_VALUE_PATTERN: Regex = Regex(
        """^NEXT VALUE FOR\s+(?:(\[(?:[^\]]|]])+]|"(?:[^"]|"")+"|[^.\s\["]+)\.)?(\[(?:[^\]]|]])+]|"(?:[^"]|"")+"|[^.\s\["]+)$""",
        RegexOption.IGNORE_CASE
    )

    private val GENERATED_KEY_NAME_PATTERN: Regex = Regex("""^(?:PK|UQ)__.+__[0-9A-F]{16}$""")
    private val GENERATED_FOREIGN_KEY_NAME_PATTERN: Regex = Regex("""^FK__.+__.+__[0-9A-F]{8}$""")
}
