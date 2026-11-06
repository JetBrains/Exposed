package org.jetbrains.exposed.v1.plugin.core.tables.mapping

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.DialectSyntax
import java.math.BigDecimal
import java.util.*

/**
 * How a column's database default ends up being reflected in the generated definition.
 */
internal sealed interface DefaultValue {
    /** Generated as `.default(<code>)`, using a literal of the column's Kotlin type. */
    data class Literal(val code: CodeBlock) : DefaultValue

    /** Generated as `.defaultExpression(<code>)`, using a built-in expression object. */
    data class Expression(val code: CodeBlock) : DefaultValue

    /** A default the generator cannot express, preserved as reported for logging. */
    data class Unsupported(val reported: String) : DefaultValue
}

private val STRING_LITERAL: Regex = Regex("""^'((?:[^']|'')*)'$""", RegexOption.DOT_MATCHES_ALL)
private val INTEGER_LITERAL: Regex = Regex("""^-?\d+$""")
private val DECIMAL_LITERAL: Regex = Regex("""^-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?$""")
private val UUID_LITERAL: Regex = Regex("""^'([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})'$""")
private val DATE_LITERAL: Regex = Regex("""^DATE '(\d{4})-(\d{2})-(\d{2})'$""", RegexOption.IGNORE_CASE)
private val TIME_LITERAL: Regex = Regex("""^TIME '(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?'$""", RegexOption.IGNORE_CASE)
private val TIMESTAMP_LITERAL: Regex = Regex(
    """^TIMESTAMP '(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?'$""",
    RegexOption.IGNORE_CASE
)

/**
 * Maps the default of a column, as the database reports it in `COLUMN_DEF` (quotes included), to the Exposed method
 * that declares it for a column generated with this definition, or to [DefaultValue.Unsupported].
 *
 * Supported defaults include:
 * - integer, floating point, decimal, boolean, string and single-character literals, Uuid & UUID literals
 * - `DATE`, `TIME` and `TIMESTAMP` literals for the `kotlinx.datetime` and `java.time` modules
 * - a value from a generated enumeration
 * - `CURRENT_TIMESTAMP` and `CURRENT_DATE` functions
 */
internal fun ColumnDefinition.defaultValueFor(
    reported: String,
    dateTimeColumnType: DateTimeColumnType,
    syntax: DialectSyntax,
): DefaultValue {
    val value = syntax.normalizeDefault(reported).trim()

    literalDefault(value, dateTimeColumnType)?.let { return DefaultValue.Literal(it) }

    currentTimeExpression(value.uppercase(), dateTimeColumnType)?.let { return DefaultValue.Expression(it) }

    return DefaultValue.Unsupported(reported)
}

@Suppress("CyclomaticComplexMethod")
private fun ColumnDefinition.literalDefault(
    value: String,
    dateTimeColumnType: DateTimeColumnType,
): CodeBlock? = when (function) {
    ColumnFunctions.BYTE, ColumnFunctions.SHORT, ColumnFunctions.INTEGER -> value.takeIf(INTEGER_LITERAL::matches)
        ?.let { CodeBlock.of("%L", it) }
    ColumnFunctions.LONG -> value.takeIf(INTEGER_LITERAL::matches)?.let { CodeBlock.of("%LL", it) }
    ColumnFunctions.FLOAT -> value.takeIf(DECIMAL_LITERAL::matches)?.let { CodeBlock.of("%Lf", it) }
    ColumnFunctions.DOUBLE -> value.takeIf(DECIMAL_LITERAL::matches)?.let { CodeBlock.of("%L", it.asDoubleLiteral()) }
    ColumnFunctions.DECIMAL -> value.takeIf(DECIMAL_LITERAL::matches)?.let { CodeBlock.of("%T(%S)", BigDecimal::class, it) }
    ColumnFunctions.BOOL -> booleanLiteral(value)
    ColumnFunctions.VARCHAR, ColumnFunctions.TEXT -> value.stringLiteral()?.let { CodeBlock.of("%S", it) }
    ColumnFunctions.CHAR -> value.stringLiteral()?.let(::charLiteral)
    ColumnFunctions.UUID -> UUID_LITERAL.matchEntire(value)
        ?.let { CodeBlock.of("%T.parse(%S)", UUID_TYPE_NAME, it.groupValues[1]) }
    ColumnFunctions.JAVA_UUID -> UUID_LITERAL.matchEntire(value)
        ?.let { CodeBlock.of("%T.fromString(%S)", UUID::class, it.groupValues[1]) }
    ColumnFunctions.CUSTOM_ENUMERATION -> value.stringLiteral()?.let(::enumerationLiteral)
    ColumnFunctions.DATE -> temporalLiteral(dateTimeColumnType, DATE_LITERAL.matchEntire(value), fieldCount = DATE_FIELDS)
    ColumnFunctions.TIME -> temporalLiteral(dateTimeColumnType, TIME_LITERAL.matchEntire(value), fieldCount = TIME_FIELDS)
    ColumnFunctions.DATETIME -> temporalLiteral(
        dateTimeColumnType,
        TIMESTAMP_LITERAL.matchEntire(value),
        fieldCount = TIMESTAMP_FIELDS
    )
    else -> null
}

private fun String.asDoubleLiteral(): String = if ('.' in this || 'e' in this || 'E' in this) this else "$this.0"

private fun booleanLiteral(value: String): CodeBlock? = when (value.uppercase()) {
    "TRUE" -> CodeBlock.of("true")
    "FALSE" -> CodeBlock.of("false")
    else -> null
}

private fun String.stringLiteral(): String? = STRING_LITERAL.matchEntire(this)?.groupValues?.get(1)?.replace("''", "'")

private val CHAR_TYPE_NAME: ClassName = ClassName("kotlin", "Char")

// `char()` without a length stores a single `Char`, while `char(n)` stores a `String`
private fun ColumnDefinition.charLiteral(text: String): CodeBlock? = when (typeName) {
    CHAR_TYPE_NAME -> text.singleOrNull()?.let { CodeBlock.of("%L", "'${it.escapedChar()}'") }
    else -> CodeBlock.of("%S", text)
}

private fun ColumnDefinition.enumerationLiteral(constant: String): CodeBlock? = if (
    nestedType?.enumConstants?.containsKey(constant) == true
) {
    CodeBlock.of("%T.%N", typeName, constant)
} else {
    null
}

private fun Char.escapedChar(): String = when (this) {
    '\'' -> "\\'"
    '\\' -> "\\\\"
    else -> toString()
}

private const val DATE_FIELDS: Int = 3
private const val TIME_FIELDS: Int = 3
private const val TIMESTAMP_FIELDS: Int = 6
private const val NANOS_DIGITS: Int = 9

/**
 * Builds the constructor call of the column's date/time type from a typed literal [match]. Its first [fieldCount]
 * captures are the integer fields (year, month, day and/or hour, minute, second) and an optional further capture is
 * the fraction of a second, right-padded to nanoseconds. Uses the `kotlinx.datetime` constructors or the `java.time`
 * `of()` factories; Joda-Time literals are not generated, since its `DateTime` constructor is zone-dependent.
 */
private fun ColumnDefinition.temporalLiteral(
    dateTimeColumnType: DateTimeColumnType,
    match: MatchResult?,
    fieldCount: Int,
): CodeBlock? {
    if (match == null || typeName !is ClassName) return null

    val fields = match.groupValues.subList(1, 1 + fieldCount).map { it.toInt() }
    val fraction = match.groupValues.getOrNull(1 + fieldCount).orEmpty()
    val nanos = fraction.takeIf { it.isNotEmpty() }?.padEnd(NANOS_DIGITS, '0')?.toInt()
    val arguments = (fields + listOfNotNull(nanos)).joinToString(", ")
    return when (dateTimeColumnType) {
        DateTimeColumnType.KOTLIN_DATETIME -> CodeBlock.of("%T($arguments)", typeName)
        DateTimeColumnType.JAVA_TIME -> CodeBlock.of("%T.of($arguments)", typeName)
        DateTimeColumnType.JODA_TIME -> null
    }
}

private fun ColumnDefinition.currentTimeExpression(
    function: String,
    dateTimeColumnType: DateTimeColumnType,
): CodeBlock? {
    val objectName = when (function) {
        "CURRENT_TIMESTAMP" if this.function == ColumnFunctions.DATETIME -> "CurrentDateTime"
        "CURRENT_TIMESTAMP" if this.function == ColumnFunctions.TIMESTAMP_WITH_TIME_ZONE &&
            dateTimeColumnType != DateTimeColumnType.JODA_TIME -> "CurrentTimestampWithTimeZone"
        "CURRENT_DATE" if this.function == ColumnFunctions.DATE -> "CurrentDate"
        else -> return null
    }
    return CodeBlock.of("%T", ClassName(dateTimeColumnType.functionPackage, objectName))
}
