package org.jetbrains.exposed.v1.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import java.io.InputStream

/**
 * Column for storing JSON data in binary format.
 *
 * @param serialize Function that encodes an object of type [T] to a JSON String
 * @param deserialize Function that decodes a JSON String to an object of type [T]
 * @sample jsonb
 */
class JsonBColumnType<T : Any>(
    serialize: (T) -> String,
    deserialize: (String) -> T
) : JsonColumnType<T>(serialize, deserialize) {
    override val usesBinaryFormat: Boolean = true

    override fun sqlType(): String = when (currentDialect) {
        is H2Dialect -> (currentDialect as H2Dialect).originalDataTypeProvider.jsonBType()
        else -> currentDialect.dataTypeProvider.jsonBType()
    }

    override fun nonNullValueToString(value: T): String {
        return when (currentDialect) {
            is SQLiteDialect -> "JSONB('${super.notNullValueToDB(value)}')"
            else -> super.nonNullValueToString(value)
        }
    }

    override fun nonNullValueAsDefaultString(value: T): String {
        return when (currentDialect) {
            is SQLiteDialect -> "(${nonNullValueToString(value)})"
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        when (currentDialect) {
            is SQLiteDialect -> {
                when (val toSetValue = (value as? String)?.encodeToByteArray()?.inputStream() ?: value) {
                    is InputStream -> stmt.setInputStream(index, toSetValue, false)
                    null -> stmt.setNull(index, this)
                    else -> super.setParameter(stmt, index, toSetValue)
                }
            }
            else -> super.setParameter(stmt, index, value)
        }
    }
}

/**
 * Creates a column, with the specified [name], for storing JSON data in decomposed binary format.
 *
 * **Note**: JSON storage in binary format is not supported by all vendors; please check the documentation.
 *
 * @param name Name of the column
 * @param serialize Function that encodes an object of type [T] to a JSON String
 * @param deserialize Function that decodes a JSON string to an object of type [T]
 */
fun <T : Any> Table.jsonb(
    name: String,
    serialize: (T) -> String,
    deserialize: (String) -> T
): Column<T> =
    registerColumn(name, JsonBColumnType(serialize, deserialize))

/**
 * Creates a column, with the specified [name], for storing JSON data in decomposed binary format.
 *
 * **Note**: JSON storage in binary format is not supported by all vendors; please check the documentation.
 *
 * @param name Name of the column
 * @param jsonConfig Configured instance of the `Json` class
 * @param kSerializer Serializer responsible for the representation of a serial form of type [T].
 * Defaults to a generic serializer for type [T]
 * @sample org.jetbrains.exposed.v1.json.JsonBColumnTests.testLoggerWithJsonBCollections
 */
inline fun <reified T : Any> Table.jsonb(
    name: String,
    jsonConfig: Json,
    kSerializer: KSerializer<T> = serializer<T>()
): Column<T> =
    jsonb(name, { jsonConfig.encodeToString(kSerializer, it) }, { jsonConfig.decodeFromString(kSerializer, it) })
