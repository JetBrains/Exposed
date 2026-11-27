package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

/**
 * Information specific for exactly how to interpret strings and codes reported by a MariaDB database.
 */
internal object MariaDbSyntax : DialectSyntax {
    override val usesJdbcTypeFallback: Boolean
        get() = false

    override val generatesColumnComments: Boolean
        get() = true

    // MariaDB has a (newer) native `UUID` type, while Exposed's `uuid()` and `javaUUID()` declare `BINARY(16)`
    override val mapsUuidType: Boolean
        get() = false

    // An enumeration comes with values, for example `enum('Bar','Baz')`
    override fun enumerationValues(declaredType: String): List<String>? {
        val quotedValues = ENUM_TYPE_PATTERN.matchEntire(declaredType)?.groupValues?.get(1) ?: return null
        return ENUM_VALUE_PATTERN.findAll(quotedValues).map { it.groupValues[1].replace("''", "'") }.toList()
    }

    // A sequence default is always reported as `nextval(`database`.`sequence`)`, however it was declared
    override fun sequenceInDefault(defaultValue: String): SequenceReference? {
        val match = NEXTVAL_PATTERN.matchEntire(defaultValue.trim()) ?: return null
        return SequenceReference(
            schema = match.groupValues[1].replace("``", "`").ifEmpty { null },
            name = match.groupValues[2].replace("``", "`"),
        )
    }

    // A primary key is always named `PRIMARY`. An unnamed foreign key is numbered within its table, `1`, `2`, and so on,
    // or named `<table>_ibfk_<n>` if the table was created by an older server. An unnamed `UNIQUE` constraint is named
    // after its first column, with a number appended when that name is taken.
    override fun isGeneratedConstraintName(name: String, tableName: String, columnNames: List<String>): Boolean {
        if (name.equals(PRIMARY_KEY_NAME, ignoreCase = true)) return true
        if (name.isNotEmpty() && name.all(Char::isDigit)) return true
        if (name.startsWith("${tableName}_ibfk_") && name.substringAfterLast('_').all(Char::isDigit)) return true
        val firstColumn = columnNames.firstOrNull() ?: return false
        return name.equals(firstColumn, ignoreCase = true) ||
            (
                name.startsWith("${firstColumn}_", ignoreCase = true) &&
                    name.substring(firstColumn.length + 1).all(Char::isDigit)
                )
    }

    override fun normalizeDefault(reported: String): String {
        val value = reported.trim()
        return when {
            CURRENT_TIMESTAMP_PATTERN.matches(value) -> "CURRENT_TIMESTAMP"
            value.equals("curdate()", ignoreCase = true) -> "CURRENT_DATE"
            else -> reported
        }
    }

    private const val PRIMARY_KEY_NAME: String = "PRIMARY"

    /** Matches an enumeration type, capturing the quoted value list. */
    private val ENUM_TYPE_PATTERN: Regex = Regex("""^enum\((.*)\)$""", RegexOption.IGNORE_CASE)

    /** Matches one quoted value of an enumeration type, in which a quote is escaped by doubling it. */
    private val ENUM_VALUE_PATTERN: Regex = Regex("""'((?:[^']|'')*)'""")

    /** Matches `nextval([`database`.]`sequence`)`, in which a backtick within a name is escaped by doubling it. */
    private val NEXTVAL_PATTERN: Regex = Regex("""^nextval\((?:`((?:[^`]|``)+)`\.)?`((?:[^`]|``)+)`\)$""", RegexOption.IGNORE_CASE)

    private val CURRENT_TIMESTAMP_PATTERN: Regex = Regex("""current_timestamp(?:\(\d*\))?""", RegexOption.IGNORE_CASE)
}
