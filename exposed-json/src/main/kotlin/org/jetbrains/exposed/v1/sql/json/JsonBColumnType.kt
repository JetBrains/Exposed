package org.jetbrains.exposed.v1.sql.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect

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
 * @sample org.jetbrains.exposed.v1.sql.json.JsonBColumnTests.testLoggerWithJsonBCollections
 */
inline fun <reified T : Any> Table.jsonb(
    name: String,
    jsonConfig: Json,
    kSerializer: KSerializer<T> = serializer<T>()
): Column<T> =
    jsonb(name, { jsonConfig.encodeToString(kSerializer, it) }, { jsonConfig.decodeFromString(kSerializer, it) })
