package org.jetbrains.exposed.v1.sql.functions.array

import org.jetbrains.exposed.v1.sql.Expression
import org.jetbrains.exposed.v1.sql.Function
import org.jetbrains.exposed.v1.sql.IColumnType
import org.jetbrains.exposed.v1.sql.QueryBuilder
import org.jetbrains.exposed.v1.sql.append
import org.jetbrains.exposed.v1.sql.vendors.H2Dialect
import org.jetbrains.exposed.v1.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.v1.sql.vendors.currentDialect
import org.jetbrains.exposed.v1.sql.vendors.h2Mode

/**
 * Represents an SQL function that returns the array element stored at the one-based [index] position,
 * or `null` if the stored array itself is null.
 */
class ArrayGet<E, T : List<E>?>(
    /** The array expression that is accessed. */
    val expression: Expression<T>,
    /** The one-based index position at which the stored array is accessed. */
    val index: Int,
    columnType: IColumnType<E & Any>
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
    columnType: IColumnType<T & Any>
) : Function<T>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        val functionProvider = when (currentDialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.PostgreSQL -> H2FunctionProvider
            else -> currentDialect.functionProvider
        }
        functionProvider.arraySlice(expression, lower, upper, queryBuilder)
    }
}
