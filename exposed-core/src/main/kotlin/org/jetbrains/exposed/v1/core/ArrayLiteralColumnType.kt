package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException

/**
 * One-dimensional PostgreSQL array column type that exchanges values with the database using the array's **text**
 * representation (`{elem,elem,...}`), instead of relying on the driver's native array support.
 *
 * [ArrayColumnType] binds values through `java.sql.Array` (JDBC) or a typed Java array (R2DBC), which only works for
 * element types that the driver has a codec for. This column type is meant for element types that a driver cannot
 * handle, like user-defined enum or composite types, and behaves the same way with both JDBC and R2DBC drivers.
 *
 * Each element is converted using the [delegate] column type:
 * - When writing, the value returned by [IColumnType.notNullValueToDB] is converted to its string representation
 * and then quoted and escaped as an array element. The string must therefore be a valid PostgreSQL input literal
 * for the element type (for a composite type this is, for example, `(1,"some text")`).
 * - When reading, each unquoted element is passed as a `String` to [IColumnType.valueFromDB]. If the driver
 * has already decoded the array, each decoded element is passed to [IColumnType.valueFromDB] instead.
 *
 * SQL `NULL` elements are supported if [E] is nullable.
 *
 * Elements are separated by [delimiter], which PostgreSQL defines per element type (`pg_type.typdelim`). It is a
 * comma for almost all types, except for the built-in `box` type, which uses a semicolon, and for user-defined
 * base types created with a different `DELIMITER`.
 *
 * **Note** This column type is only supported by PostgreSQL dialects. JDBC drivers decode the array themselves when
 * reading, so [delegate] must also be able to handle the element values they produce. For example, the pgjdbc-ng driver
 * decodes composite elements into `java.sql.Struct` objects and `box` elements into `DoubleArray` objects.
 *
 * @property delegate The base column type associated with this array column's individual elements.
 * @property delimiter The character that separates elements in the array's text representation.
 */
class ArrayLiteralColumnType<E>(
    val delegate: IColumnType<E & Any>,
    val delimiter: Char = ','
) : ColumnType<List<E>>() {
    init {
        require(delimiter !in PgArrayLiteral.RESERVED_CHARACTERS && !delimiter.isWhitespace()) {
            "Invalid array delimiter '$delimiter'"
        }
    }

    override fun sqlType(): String {
        val dialect = currentDialect
        if (dialect !is PostgreSQLDialect) {
            throw UnsupportedByDialectException("Array literal columns are only supported by PostgreSQL", dialect)
        }
        return "${delegate.sqlType()}[]"
    }

    /**
     * The value is bound as a string, so it is cast explicitly to the array type, as PostgreSQL has no
     * implicit cast from a string type to an array type. The intermediate cast to `text` ensures that the
     * parameter type is inferred as `text`, as some drivers would otherwise expect the array type itself to be bound.
     */
    override fun parameterMarker(value: List<E>?): String = "?::text::${sqlType()}"

    override fun notNullValueToDB(value: List<E>): Any = value.joinToString(delimiter.toString(), "{", "}") { element ->
        element?.let { PgArrayLiteral.quote(elementToString(it)) } ?: PgArrayLiteral.NULL_ELEMENT
    }

    private fun elementToString(element: E & Any): String = when (val dbValue = delegate.notNullValueToDB(element)) {
        is ByteArray -> dbValue.joinToString("", prefix = "\\x") { "%02x".format(it) }
        else -> dbValue.toString()
    }

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): List<E> = when (value) {
        is String -> PgArrayLiteral.parse(value, delimiter).map { element -> element?.let { delegate.valueFromDB(it) } as E }
        is Array<*> -> value.map { element -> element?.let { delegate.valueFromDB(it) } as E }
        is java.sql.Array -> valueFromDB(value.array)
        is List<*> -> value as List<E>
        else -> error("Unexpected value of type ${value::class.qualifiedName} for column type ${sqlType()}: $value")
    }

    override fun nonNullValueToString(value: List<E>): String {
        val literal = notNullValueToDB(value) as String
        return "'${literal.replace("'", "''")}'::${sqlType()}"
    }
}

/**
 * Helpers for the PostgreSQL array text grammar, as documented in
 * [Array Value Input](https://www.postgresql.org/docs/current/arrays.html#ARRAYS-IO).
 */
internal object PgArrayLiteral {
    /** The unquoted token that represents a SQL `NULL` element. It is matched case-insensitively. */
    const val NULL_ELEMENT = "NULL"

    /** Characters with a special meaning in an array literal, which therefore cannot be used as a delimiter. */
    const val RESERVED_CHARACTERS = "{}\"\\"

    /** Double-quotes [value], escaping any backslash and double-quote characters. */
    fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { c ->
            if (c == '\\' || c == '"') append('\\')
            append(c)
        }
        append('"')
    }

    /**
     * Parses a one-dimensional array literal, with elements separated by [delimiter], into its elements, with SQL `NULL` elements returned as `null`.
     *
     * A quoted element is taken verbatim (after unescaping), so a quoted `"NULL"` is the string `NULL` and never a
     * SQL `NULL`. Whitespace around unquoted elements is ignored, as it is by PostgreSQL.
     */
    fun parse(literal: String, delimiter: Char): List<String?> {
        val body = unwrap(literal)
        if (body.isBlank()) return emptyList()

        val elements = ArrayList<String?>()
        val current = StringBuilder()
        var inQuotes = false
        var wasQuoted = false
        var i = 0

        fun completeElement() {
            val element = when {
                wasQuoted -> current.toString()
                else -> current.toString().trim().also {
                    require(it.isNotEmpty()) { "Malformed array literal, unexpected empty element: $literal" }
                }.takeUnless { it.equals(NULL_ELEMENT, ignoreCase = true) }
            }
            elements.add(element)
            current.setLength(0)
            wasQuoted = false
        }

        while (i < body.length) {
            when (val c = body[i]) {
                '\\' -> {
                    require(i + 1 < body.length) { "Malformed array literal, dangling escape character: $literal" }
                    current.append(body[++i])
                }
                '"' -> {
                    inQuotes = !inQuotes
                    wasQuoted = true
                }
                delimiter if !inQuotes -> completeElement()
                '{' if !inQuotes -> error("Multidimensional array literals are not supported: $literal")
                else -> current.append(c)
            }
            i++
        }
        require(!inQuotes) { "Malformed array literal, unterminated quoted element: $literal" }
        completeElement()

        return elements
    }

    /** Strips any explicit dimension decoration (e.g. `[0:1]=`) and the enclosing braces. */
    private fun unwrap(literal: String): String {
        val trimmed = literal.trim().let {
            if (it.startsWith('[')) it.substringAfter('=').trim() else it
        }
        require(trimmed.length >= 2 && trimmed.first() == '{' && trimmed.last() == '}') {
            "Malformed array literal, expected value enclosed in braces: $literal"
        }
        return trimmed.substring(1, trimmed.length - 1)
    }
}
