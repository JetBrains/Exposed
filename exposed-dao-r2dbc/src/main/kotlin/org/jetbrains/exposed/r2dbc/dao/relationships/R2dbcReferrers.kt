package org.jetbrains.exposed.r2dbc.dao.relationships

import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.emptySized
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KProperty

class R2dbcReferrers<ParentID : Any, in Parent : R2dbcEntity<ParentID>, ChildID : Any, out Child : R2dbcEntity<ChildID>, REF>(
    val reference: Column<REF>,
    val factory: R2dbcEntityClass<ChildID, Child>,
    val cache: Boolean
) {
    /** The set of columns and their [SortOrder] for ordering referred entities in one-to-many relationship. */
    private val orderByExpressions = linkedSetOf<Pair<Expression<*>, SortOrder>>()

    /** Returns the order by expressions as an array. */
    internal fun getOrderByExpressions(): Array<Pair<Expression<*>, SortOrder>> = orderByExpressions.toTypedArray()

    init {
        reference.referee ?: error("Column $reference is not a reference")
        if (factory.table != reference.table) {
            error("Column $reference and factory ${factory.table.tableName} point to different tables")
        }
    }

    @Suppress("NestedBlockDepth", "SpreadOperator")
    operator fun getValue(thisRef: Parent, property: KProperty<*>): suspend () -> SizedIterable<Child> = {
        val transaction = TransactionManager.currentOrNull()

        if (transaction == null) {
            // Out-of-transaction access falls back to the per-entity reference cache populated when
            // `keepLoadedReferencesOutOfTransaction = true` (or by an eager loader like `with(...)`).
            if (thisRef.id._value == null) {
                emptySized()
            } else if (thisRef.hasInReferenceCache(reference)) {
                val cached = thisRef.getReferenceFromCache<Any?>(reference)
                @Suppress("UNCHECKED_CAST")
                when (cached) {
                    is SizedIterable<*> -> cached as SizedIterable<Child>
                    null -> emptySized()
                    else -> error("Cached referrer has unexpected type: ${cached::class}")
                }
            } else {
                error("No transaction in context, and referrers not in entity cache for $reference")
            }
        } else {
            // Mirrors JDBC's implicit flush via `DaoEntityID.invokeOnNoValue` on `id.value` access.
            if (thisRef.id._value == null) {
                transaction.entityCache.flush()
            }

            val referee = reference.referee!!
            val refereeValue = with(thisRef) { referee.lookup() }

            val needsEntityIdUnwrap = reference.columnType !is EntityIDColumnType<*> &&
                referee.columnType is EntityIDColumnType<*> && refereeValue is EntityID<*>

            @Suppress("UNCHECKED_CAST")
            val refValue = if (needsEntityIdUnwrap) refereeValue.value as REF else refereeValue as REF

            val query: suspend () -> SizedIterable<Child> = {
                factory.find { reference eq refValue }
                    .orderBy(*orderByExpressions.toTypedArray())
            }

            val result = if (cache) {
                @Suppress("UNCHECKED_CAST")
                transaction.entityCache.getOrPutReferrers(reference, thisRef.id, query)
            } else {
                query()
            }

            thisRef.storeReferenceInCache(reference, result)
            result
        }
    }

    /** Modifies this reference to sort entities based on multiple columns as specified in [order]. */
    infix fun orderBy(order: List<Pair<Expression<*>, SortOrder>>) = this.also {
        orderByExpressions.addAll(order)
    }

    /** Modifies this reference to sort entities according to the specified [order]. */
    infix fun orderBy(order: Pair<Expression<*>, SortOrder>) = orderBy(listOf(order))

    /** Modifies this reference to sort entities by a column specified in [expression] using ascending order. */
    infix fun orderBy(expression: Expression<*>) = orderBy(listOf(expression to SortOrder.ASC))

    /** Modifies this reference to sort entities based on multiple columns as specified in [order]. */
    fun orderBy(vararg order: Pair<Expression<*>, SortOrder>) = orderBy(order.asList())
}
