package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedEntityMapper
import java.lang.reflect.Method

internal class ExposedQueryContext(
    val repository: Any,
    val mapper: ExposedEntityMapper<*>,
    val table: IdTable<*>,
    val method: Method,
)

internal object ExposedQueryContextHolder {
    private val currentContext: ThreadLocal<ExposedQueryContext?> = ThreadLocal.withInitial {
        null
    }

    fun get(): ExposedQueryContext? = currentContext.get()
    fun set(ctx: ExposedQueryContext) = currentContext.set(ctx)
    fun clear() = currentContext.remove()

    inline fun <R> withContext(ctx: ExposedQueryContext, block: () -> R): R {
        val previousContext = get()
        set(ctx)
        try {
            return block()
        } finally {
            if (previousContext != null) set(previousContext) else clear()
        }
    }
}

