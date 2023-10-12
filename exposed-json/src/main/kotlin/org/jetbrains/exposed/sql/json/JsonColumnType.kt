package org.jetbrains.exposed.sql.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.JsonColumnMarker
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.postgresql.util.PGobject

/**
 * Column for storing JSON data, either in non-binary text format or the vendor's default JSON type format.
 *
 * @sample json
 */
open class JsonColumnType<T : Any>(
    /** Encode an object of type [T] to a JSON String. */
    val serialize: (T) -> String,
    /** Decode a JSON String to an object of type [T]. */
    val deserialize: (String) -> T
) : ColumnType(), JsonColumnMarker {
    override val usesBinaryFormat: Boolean = false

    override fun sqlType(): String = currentDialect.dataTypeProvider.jsonType()

    override fun valueFromDB(value: Any): Any {
        return when {
            currentDialect is PostgreSQLDialect && value is PGobject -> deserialize(value.value!!)
            value is String -> deserialize(value)
            value is ByteArray -> deserialize(value.decodeToString())
            else -> value
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun notNullValueToDB(value: Any) = serialize(value as T)

    override fun valueToString(value: Any?): String = when (value) {
        is Iterable<*> -> nonNullValueToString(value)
        else -> super.valueToString(value)
    }

    override fun nonNullValueToString(value: Any): String {
        return when (currentDialect) {
            is H2Dialect -> "JSON '${notNullValueToDB(value)}'"
            else -> super.nonNullValueToString(value)
        }
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue = when (currentDialect) {
            is PostgreSQLDialect -> PGobject().apply {
                type = sqlType()
                this.value = value as String?
            }
            is H2Dialect -> (value as String).encodeToByteArray()
            else -> value
        }
        super.setParameter(stmt, index, parameterValue)
    }
}

/**
 * Creates a column, with the specified [name], for storing JSON data.
 *
 * **Note**: This column stores JSON either in non-binary text format or,
 * if the vendor only supports 1 format, the default JSON type format.
 * If JSON must be stored in binary format, and the vendor supports this, please use `jsonb()` instead.
 *
 * @param name Name of the column
 * @param serialize Function that encodes an object of type [T] to a JSON String
 * @param deserialize Function that decodes a JSON string to an object of type [T]
 */
fun <T : Any> Table.json(
    name: String,
    serialize: (T) -> String,
    deserialize: (String) -> T
): Column<T> =
    registerColumn(name, JsonColumnType(serialize, deserialize))

/**
 * Creates a column, with the specified [name], for storing JSON data.
 *
 * **Note**: This column stores JSON either in non-binary text format or,
 * if the vendor only supports 1 format, the default JSON type format.
 * If JSON must be stored in binary format, and the vendor supports this, please use `jsonb()` instead.
 *
 * @param name Name of the column
 * @param jsonConfig Configured instance of the `Json` class
 * @param kSerializer Serializer responsible for the representation of a serial form of type [T].
 * Defaults to a generic serializer for type [T]
 * @sample org.jetbrains.exposed.sql.json.JsonColumnTests.testLoggerWithJsonCollections
 */
inline fun <reified T : Any> Table.json(
    name: String,
    jsonConfig: Json,
    kSerializer: KSerializer<T> = serializer<T>()
): Column<T> =
    json(name, { jsonConfig.encodeToString(kSerializer, it) }, { jsonConfig.decodeFromString(kSerializer, it) })
