package org.jetbrains.exposed.v1.spring.data.query

import org.jetbrains.exposed.v1.spring.data.mapping.ProjectionResolver
import java.lang.reflect.Method

/** The repository call a `query { }` block runs in. */
internal class ExposedQueryContext(
    val repository: Any,
    val method: Method,
    val projectionResolver: ProjectionResolver<*, *>,
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
