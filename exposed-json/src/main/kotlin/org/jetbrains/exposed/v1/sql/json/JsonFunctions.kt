@file: Suppress("MatchingDeclarationName")

package org.jetbrains.exposed.v1.sql.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.sql.*

// Function Classes

/**
 * Represents an SQL function that returns extracted data from a JSON object at the specified [path],
 * either as a JSON representation or as a scalar value.
 */
class Extract<T>(
    /** The expression from which to extract JSON subcomponents matched by [path]. */
    val expression: Expression<*>,
    /** Array of Strings representing JSON path/keys that match fields to be extracted. */
    vararg val path: String,
    /** Whether the extracted result should be a scalar or text value; if `false`, result will be a JSON object. */
    val toScalar: Boolean,
    /** The column type of [expression] to check, if casting to JSONB is required. */
    val jsonType: IColumnType<*>,
    columnType: IColumnType<T & Any>
) : Function<T>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        currentDialect.functionProvider.jsonExtract(expression, path = path, toScalar, jsonType, queryBuilder)
}

// Extension Functions

/**
 * Returns the extracted data from a JSON object at the specified [path], either as a JSON representation or as a scalar value.
 *
 * @param path String(s) representing JSON path/keys that match fields to be extracted.
 * If none are provided, the root context item `'$'` will be used by default.
 * **Note:** Multiple [path] arguments are not supported by all vendors; please check the documentation.
 * @param toScalar If `true`, the extracted result is a scalar or text value; otherwise, it is a JSON object.
 * @sample org.jetbrains.exposed.v1.sql.json.JsonColumnTests.testJsonExtractWithArrays
 */
inline fun <reified T : Any> ExpressionWithColumnType<*>.extract(
    vararg path: String,
    toScalar: Boolean = true
): Extract<T> {
    @OptIn(InternalApi::class)
    val columnType = resolveColumnType(
        T::class,
        defaultType = JsonColumnType(
            { Json.Default.encodeToString(serializer<T>(), it) },
            { Json.Default.decodeFromString(serializer<T>(), it) }
        )
    )
    return Extract(this, path = path, toScalar, this.columnType, columnType)
}
