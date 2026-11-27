package org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect

/**
 * Information specific for exactly how to interpret strings and codes reported by a MySQL database.
 */
internal object MySqlSyntax : DialectSyntax {
    override val usesJdbcTypeFallback: Boolean
        get() = false

    override val generatesColumnComments: Boolean
        get() = true

    // An enumeration comes with values, for example `enum('Bar','Baz')`
    override fun enumerationValues(declaredType: String): List<String>? {
        val quotedValues = ENUM_TYPE_PATTERN.matchEntire(declaredType)?.groupValues?.get(1) ?: return null
        return ENUM_VALUE_PATTERN.findAll(quotedValues).map { it.groupValues[1].replace("''", "'") }.toList()
    }

    // A primary key is always named `PRIMARY`, an unnamed foreign key `<table>_ibfk_<n>`, and an unnamed `UNIQUE`
    // constraint after its first column, with a number appended when that name is taken.
    override fun isGeneratedConstraintName(name: String, tableName: String, columnNames: List<String>): Boolean {
        if (name.equals(PRIMARY_KEY_NAME, ignoreCase = true)) return true
        if (name.startsWith("${tableName}_ibfk_") && name.substringAfterLast('_').all(Char::isDigit)) return true
        val firstColumn = columnNames.firstOrNull() ?: return false
        return name.equals(firstColumn, ignoreCase = true) ||
            (name.startsWith("${firstColumn}_", ignoreCase = true) && name.substring(firstColumn.length + 1).all(Char::isDigit))
    }

    private const val PRIMARY_KEY_NAME: String = "PRIMARY"

    /** Matches an enumeration type, capturing the quoted value list. */
    private val ENUM_TYPE_PATTERN: Regex = Regex("""^enum\((.*)\)$""", RegexOption.IGNORE_CASE)

    /** Matches one quoted value of an enumeration type, in which a quote is escaped by doubling it. */
    private val ENUM_VALUE_PATTERN: Regex = Regex("""'((?:[^']|'')*)'""")
}
