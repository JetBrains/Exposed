package org.jetbrains.exposed.v1.postgresql.hstore

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException

/**
 * Column for storing PostgreSQL `hstore` key-value data.
 *
 * **Note**: This type is only supported by PostgreSQL and requires the `hstore` extension to be enabled
 * on the target database (`CREATE EXTENSION IF NOT EXISTS hstore;`).
 *
 * Keys cannot be `null`; values may be `null`.
 *
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.hstore
 */
class HstoreColumnType : ColumnType<Map<String, String?>>() {
    override fun sqlType(): String {
        val dialect = currentDialect
        if (dialect !is PostgreSQLDialect) {
            throw UnsupportedByDialectException("The HSTORE column type is only supported by PostgreSQL", dialect)
        }
        return "HSTORE"
    }

    override fun parameterMarker(value: Map<String, String?>?): String = when (currentDialect) {
        is PostgreSQLDialect -> "?::hstore"
        else -> super.parameterMarker(value)
    }

    override fun notNullValueToDB(value: Map<String, String?>): Any = encodeHstore(value)

    override fun nonNullValueAsDefaultString(value: Map<String, String?>): String =
        "'${encodeHstore(value)}'::hstore"

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): Map<String, String?> = when (value) {
        is String -> decodeHstore(value)
        is Map<*, *> -> value as Map<String, String?>
        else -> error("Unexpected value $value of type ${value::class.qualifiedName}")
    }

    override fun readObject(rs: RowApi, index: Int): Any? = when (currentDialect) {
        is PostgreSQLDialect -> rs.getString(index)
        else -> super.readObject(rs, index)
    }
}

/**
 * Creates a column, with the specified [name], for storing PostgreSQL `hstore` key-value data.
 *
 * **Note**: This column type is only supported by PostgreSQL and requires the `hstore` extension to be
 * enabled on the target database (`CREATE EXTENSION IF NOT EXISTS hstore;`).
 *
 * @param name Name of the column
 * @sample org.jetbrains.exposed.v1.postgresql.hstore.HstoreColumnTests.testHstoreRoundTrip
 */
fun Table.hstore(name: String): Column<Map<String, String?>> = registerColumn(name, HstoreColumnType())

/** Encodes a Kotlin map into PostgreSQL's `hstore` text representation. */
internal fun encodeHstore(value: Map<String, String?>): String =
    value.entries.joinToString(separator = ", ") { (key, entryValue) ->
        val encodedKey = "\"${escapeHstoreLiteral(key)}\""
        val encodedValue = entryValue?.let { "\"${escapeHstoreLiteral(it)}\"" } ?: "NULL"
        "$encodedKey=>$encodedValue"
    }

private fun escapeHstoreLiteral(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

/** Decodes PostgreSQL's `hstore` text representation into a Kotlin map. */
internal fun decodeHstore(raw: String): Map<String, String?> {
    val result = LinkedHashMap<String, String?>()
    val cursor = HstoreCursor(raw)

    var key = cursor.readToken()
    while (key != null) {
        require(!key.wasNullLiteral) { "Malformed hstore value, unexpected NULL as key: $raw" }
        cursor.expect("=>")
        val entryValue = cursor.readToken() ?: error("Malformed hstore value, expected value after '=>': $raw")
        result[key.text] = if (entryValue.wasNullLiteral) null else entryValue.text
        key = if (cursor.skipSeparator()) cursor.readToken() else null
    }

    return result
}

private class HstoreCursor(private val raw: String) {
    private var i = 0

    data class Token(val text: String, val wasNullLiteral: Boolean)

    private fun skipWhitespace() {
        while (i < raw.length && raw[i].isWhitespace()) i++
    }

    fun readToken(): Token? {
        skipWhitespace()
        if (i >= raw.length) return null

        if (raw[i] == '"') {
            i++
            val sb = StringBuilder()
            while (i < raw.length && raw[i] != '"') {
                if (raw[i] == '\\' && i + 1 < raw.length) {
                    sb.append(raw[i + 1])
                    i += 2
                } else {
                    sb.append(raw[i])
                    i++
                }
            }
            require(i < raw.length) { "Malformed hstore value, unterminated quoted token: $raw" }
            i++
            return Token(sb.toString(), wasNullLiteral = false)
        }

        val start = i
        while (i < raw.length && raw[i] != ',' && raw[i] != '=') i++
        val text = raw.substring(start, i).trim()
        return Token(text, wasNullLiteral = text.equals("NULL", ignoreCase = true))
    }

    fun expect(token: String) {
        skipWhitespace()
        require(raw.startsWith(token, i)) { "Malformed hstore value, expected '$token' at index $i: $raw" }
        i += token.length
    }

    fun skipSeparator(): Boolean {
        skipWhitespace()
        if (i >= raw.length) return false
        require(raw[i] == ',') { "Malformed hstore value, expected ',' at index $i: $raw" }
        i++
        return true
    }
}
