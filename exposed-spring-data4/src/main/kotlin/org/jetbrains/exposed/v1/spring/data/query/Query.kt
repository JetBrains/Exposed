@file:OptIn(ExperimentalTypeInference::class)

package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.mapping.RowConverter
import kotlin.experimental.ExperimentalTypeInference

/**
 * Runs [block] and converts the returned rows to [T], which can be the entity, a DTO, a closed interface projection or [ResultRow].
 *
 * If [block] returns an unmodified `selectAll()` query, only the columns the projection needs are selected.
 *
 * In a native image, DTO and interface projections need runtime hints. They are registered automatically when [T] appears
 * in the return type of the repository method (e.g. `fun names(): List<CustomerName> = query { ... }`); otherwise, e.g. in a
 * generic method or if the result is mapped further, register [T] with `@RegisterReflectionForBinding`.
 */
@OverloadResolutionByLambdaReturnType
inline fun <reified T : Any> ExposedCrudRepository<*, *>.query(noinline block: () -> Iterable<ResultRow>): Iterable<T> =
    queryRows(T::class.java, block)

/** Runs [block] and converts the returned rows to [T], see the `Iterable<ResultRow>` overload. */
@OverloadResolutionByLambdaReturnType
@JvmName("queryList")
inline fun <reified T : Any> ExposedCrudRepository<*, *>.query(noinline block: () -> List<ResultRow>): List<T> =
    queryRows(T::class.java, block)

/** Runs [block] and converts the returned row to [T], see the `Iterable<ResultRow>` overload. */
@OverloadResolutionByLambdaReturnType
@JvmName("queryOne")
inline fun <reified T : Any> ExposedCrudRepository<*, *>.query(noinline block: () -> ResultRow): T =
    checkNotNull(queryRow(T::class.java, block))

/** Runs [block] and converts the returned row to [T], or returns `null` if there is no row. */
@OverloadResolutionByLambdaReturnType
@JvmName("queryOneOrNull")
inline fun <reified T : Any> ExposedCrudRepository<*, *>.query(noinline block: () -> ResultRow?): T? =
    queryRow(T::class.java, block)

@OverloadResolutionByLambdaReturnType
@JvmName("queryOneTyped")
fun <T> ExposedCrudRepository<*, *>.query(block: () -> T): T {
    requireContext()
    return block()
}

@OverloadResolutionByLambdaReturnType
@JvmName("queryManyIterable")
fun <T> ExposedCrudRepository<*, *>.query(block: () -> Iterable<T>): Iterable<T> {
    requireContext()
    // read lazy results while the transaction of the repository method is still active
    return when (val result = block()) {
        is Collection<T> -> result
        else -> result.toList()
    }
}

@OverloadResolutionByLambdaReturnType
@JvmName("queryManyList")
fun <T> ExposedCrudRepository<*, *>.query(block: () -> List<T>): List<T> {
    requireContext()
    return block()
}

@PublishedApi
internal fun <T : Any> ExposedCrudRepository<*, *>.queryRows(type: Class<T>, block: () -> Iterable<ResultRow>): List<T> {
    val ctx = requireContext()
    // resolved before the block runs, so an invalid projection fails without running any SQL
    val converter = ctx.projectionResolver.converterFor(type)
    val rows = when (val result = block()) {
        is Query -> result.narrowedTo(converter, ctx.projectionResolver.metadata.table)
        else -> result
    }
    // read the rows while the transaction of the repository method is still active
    return converter.convertAll(rows.toList())
}

@PublishedApi
internal fun <T : Any> ExposedCrudRepository<*, *>.queryRow(type: Class<T>, block: () -> ResultRow?): T? {
    val ctx = requireContext()
    val converter = ctx.projectionResolver.converterFor(type)
    return block()?.let(converter::convert)
}

/**
 * Selects only the columns of a DTO or interface projection, if the query is a plain `selectAll()` on the repository's table.
 * Explicit select lists, joins, `DISTINCT` and `GROUP BY` are left alone, because narrowing would change their result.
 */
private fun Query.narrowedTo(converter: RowConverter<*>, table: Table): Query {
    val narrow = converter.narrowsSelect &&
        converter.columns.isNotEmpty() &&
        set === table &&
        !distinct &&
        distinctOn == null &&
        groupedByColumns.isEmpty()
    return if (narrow) adjustSelect { select(converter.columns) } else this
}

private fun ExposedCrudRepository<*, *>.requireContext(): ExposedQueryContext {
    val ctx = checkNotNull(ExposedQueryContextHolder.get()) {
        "query { } can only be used inside a default method of an Exposed repository interface"
    }
    check(ctx.repository === this) {
        "query { } was called on a different repository than ${ctx.method.declaringClass.simpleName}.${ctx.method.name}"
    }
    return ctx
}
