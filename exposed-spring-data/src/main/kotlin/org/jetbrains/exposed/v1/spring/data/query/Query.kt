@file:OptIn(ExperimentalTypeInference::class)

package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import kotlin.experimental.ExperimentalTypeInference


@OverloadResolutionByLambdaReturnType
fun <T> ExposedCrudRepository<*, *>.query(block: () -> Iterable<ResultRow>): Iterable<T> {
    val ctx = requireContext()

    // TODO: This only works with existing mappers for Entities, we cannot get other mappers from T atm
    @Suppress("UNCHECKED_CAST")
    return block().map { ctx.mapper.toEntity(it) as T }
}

@OverloadResolutionByLambdaReturnType
@JvmName("queryList")
fun <T> ExposedCrudRepository<*, *>.query(block: () -> List<ResultRow>): List<T> {
    val ctx = requireContext()

    // TODO: This only works with existing mappers for Entities, we cannot get other mappers from T atm
    @Suppress("UNCHECKED_CAST")
    return block().map { ctx.mapper.toEntity(it) as T }
}

@OverloadResolutionByLambdaReturnType
@JvmName("queryOne")
fun <T> ExposedCrudRepository<*, *>.query(block: () -> ResultRow): T {
    val ctx = requireContext()

    // TODO: This only works with existing mappers for Entities, we cannot get other mappers from T atm
    @Suppress("UNCHECKED_CAST")
    return block().let { ctx.mapper.toEntity(it) as T }
}

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

private fun ExposedCrudRepository<*, *>.requireContext(): ExposedQueryContext {
    val ctx = checkNotNull(ExposedQueryContextHolder.get()) {
        "query { } can only be used inside a default method of an Exposed repository interface"
    }
    check(ctx.repository === this) {
        "query { } was called on a different repository than ${ctx.method.declaringClass.simpleName}.${ctx.method.name}"
    }
    return ctx
}
