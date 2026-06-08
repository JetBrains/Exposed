package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.r2dbc.SizedIterable

internal class DeferredQuery<T>(
    private val base: suspend () -> SizedIterable<T>,
    private val modifier: (SizedIterable<T>) -> SizedIterable<T> = { it }
) : SizedIterable<T> {
    private suspend fun get() = modifier(base())

    override suspend fun collect(collector: FlowCollector<T>) = get().collect(collector)

    override suspend fun count() = get().count()

    override suspend fun empty() = get().empty()

    override fun limit(count: Int) = DeferredQuery(base) { modifier(it).limit(count) }

    override fun offset(start: Long) = DeferredQuery(base) { modifier(it).offset(start) }

    override fun copy() = DeferredQuery(base, modifier)

    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>) =
        DeferredQuery(base) { modifier(it).orderBy(*order) }
}
