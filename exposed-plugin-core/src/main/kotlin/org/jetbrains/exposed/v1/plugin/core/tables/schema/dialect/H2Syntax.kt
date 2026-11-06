package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

/**
 * Information specific for exactly how to interpret strings and codes reported by an H2 database.
 */
internal object H2Syntax : DialectSyntax {
    // A one-dimensional array is either `<element> ARRAY` or `<element> ARRAY[n]`, for example `VARCHAR(255) ARRAY[10]`
    override fun arrayElement(declaredType: String): ArrayElement? = ARRAY_TYPE_PATTERN.matchEntire(declaredType)?.let { match ->
        ArrayElement(match.groupValues[1], match.groupValues[2].toIntOrNull())
    }

    // An enumeration comes with values, for example `ENUM('Bar', 'Baz')`
    override fun enumerationValues(declaredType: String): List<String>? {
        val quotedValues = ENUM_TYPE_PATTERN.matchEntire(declaredType)?.groupValues?.get(1) ?: return null
        return ENUM_VALUE_PATTERN.findAll(quotedValues).map { it.groupValues[1].replace("''", "'") }.toList()
    }

    // Sequences usually come schema-qualified and quoted, for example `NEXT VALUE FOR "PUBLIC"."ORDERS_SEQ"`
    override fun sequenceInDefault(defaultValue: String): SequenceReference? {
        val match = NEXT_VALUE_PATTERN.matchEntire(defaultValue.trim()) ?: return null
        return SequenceReference(schema = match.groupValues[1].ifEmpty { null }, name = match.groupValues[2])
    }

    // Unnamed constraints match `CONSTRAINT_<n>`
    override fun isGeneratedConstraintName(name: String, tableName: String, columnNames: List<String>): Boolean =
        GENERATED_CONSTRAINT_PATTERN.matches(name)

    /** Matches the element type and the optional maximum cardinality of an array type. */
    private val ARRAY_TYPE_PATTERN: Regex = Regex("""^(.+?) ARRAY(?:\[(\d+)])?$""")

    /** Matches an enumeration type, capturing the quoted value list. */
    private val ENUM_TYPE_PATTERN: Regex = Regex("""^ENUM\((.*)\)$""", RegexOption.IGNORE_CASE)

    /** Matches one quoted value of an enumeration type, in which a quote is escaped by doubling it. */
    private val ENUM_VALUE_PATTERN: Regex = Regex("""'((?:[^']|'')*)'""")

    /** Matches `NEXT VALUE FOR [schema.]sequence`, with either part optionally double-quoted. */
    private val NEXT_VALUE_PATTERN: Regex = Regex("""^NEXT VALUE FOR\s+(?:"?([^".\s]+)"?\.)?"?([^"\s]+)"?$""", RegexOption.IGNORE_CASE)

    /** Matches an unnamed or database-named constraint. */
    private val GENERATED_CONSTRAINT_PATTERN: Regex = Regex("""CONSTRAINT_[0-9A-F]+""", RegexOption.IGNORE_CASE)
}
