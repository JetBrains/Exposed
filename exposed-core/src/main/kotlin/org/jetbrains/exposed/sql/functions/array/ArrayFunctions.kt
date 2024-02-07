package org.jetbrains.exposed.sql.functions.array

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.vendors.currentDialect

/**
 * Represents an SQL function that returns the array element stored at the one-based [index] position,
 * or `null` if the stored array itself is null or if [index] is out of bounds.
 */
class ArrayElementAt<E, T : List<E>?>(
    /** The array expression that is accessed. */
    val expression: Expression<T>,
    /** The one-based index position at which the stored array is accessed. */
    val index: Int,
    columnType: IColumnType
) : Function<E?>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            append(expression, "[", index.toString(), "]")
        }
    }
}

/**
 * Represents an SQL function that returns a subarray of elements stored from between [lower] and [upper] bounds (inclusive),
 * or `null` if the stored array itself is null.
 */
class ArraySlice<E, T : List<E>?>(
    /** The array expression from which the subarray is returned. */
    val expression: Expression<T>,
    /** The lower bounds (inclusive) of a subarray. If left `null`, the database will use the stored array's lower limit. */
    val lower: Int?,
    /** The upper bounds (inclusive) of a subarray. If left `null`, the database will use the stored array's upper limit. */
    val upper: Int?,
    columnType: IColumnType
) : Function<T>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.arraySlice(expression, lower, upper, queryBuilder)
    }
}
