@file: Suppress("MatchingDeclarationName")

package org.jetbrains.exposed.sql.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.vendors.currentDialect

// Function Classes

/**
 * Represents an SQL function that returns extracted data from a JSON object at the specified [path],
 * either as a JSON representation or as a scalar value.
 */
class Extract<T>(
    /** Returns the expression from which to extract JSON subcomponents matched by [path]. */
    val expression: Expression<*>,
    /** Returns array of Strings representing JSON path/keys that match fields to be extracted. */
    vararg val path: String,
    /** Returns whether the extracted result should be a scalar or text value; if `false`, result will be a JSON object. */
    val toScalar: Boolean,
    /** Returns the column type of [expression] to check, if casting to JSONB is required. */
    val jsonType: IColumnType,
    columnType: IColumnType
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
 */
inline fun <reified T : Any> ExpressionWithColumnType<*>.extract(
    vararg path: String,
    toScalar: Boolean = true
): Extract<T> {
    val columnType = when (T::class) {
        String::class -> TextColumnType()
        Boolean::class -> BooleanColumnType()
        Long::class -> LongColumnType()
        Int::class -> IntegerColumnType()
        Short::class -> ShortColumnType()
        Byte::class -> ByteColumnType()
        Double::class -> DoubleColumnType()
        Float::class -> FloatColumnType()
        ByteArray::class -> BasicBinaryColumnType()
        else -> {
            JsonColumnType(
                { Json.Default.encodeToString(serializer<T>(), it) },
                { Json.Default.decodeFromString(serializer<T>(), it) }
            )
        }
    }
    return Extract(this, path = path, toScalar, this.columnType, columnType)
}
