package org.jetbrains.exposed.r2dbc.dao.relationships

import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.emptySized
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KProperty

class R2dbcReferrers<ParentID : Any, in Parent : R2dbcEntity<ParentID>, ChildID : Any, out Child : R2dbcEntity<ChildID>, REF>(
    val reference: Column<REF>,
    val factory: R2dbcEntityClass<ChildID, Child>,
    val cache: Boolean,
    references: Map<Column<*>, Column<*>>? = null
) {
    /** The set of columns and their [SortOrder] for ordering referred entities in one-to-many relationship. */
    private val orderByExpressions = linkedSetOf<Pair<Expression<*>, SortOrder>>()

    /** Returns the order by expressions as an array. */
    internal fun getOrderByExpressions(): Array<Pair<Expression<*>, SortOrder>> = orderByExpressions.toTypedArray()

    /**
     * Full child→parent column mapping for the relationship. Single-column references derive this from
     * `reference.referee` lazily; composite-FK references pass the full map explicitly. Mirrors JDBC's
     * `Referrers.allReferences`.
     *
     * TODO ALIGN_WITH_JDBC: not yet consumed at runtime — `getValue` below still issues a single-column
     *  `WHERE reference = value`. JDBC switches to `compoundAnd` of `eq`s when this map has multiple
     *  entries (see `References.kt`).
     */
    @Suppress("unused")
    val allReferences: Map<Column<*>, Column<*>> = references ?: run {
        reference.referee ?: error("Column $reference is not a reference")
        if (factory.table != reference.table) {
            error("Column $reference and factory ${factory.table.tableName} point to different tables")
        }
        @Suppress("UNCHECKED_CAST")
        mapOf(reference as Column<*> to reference.referee!!)
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

            val isComposite = allReferences.size > 1 || allReferences.values.firstOrNull()?.table is CompositeIdTable
            val query: suspend () -> SizedIterable<Child> = if (!isComposite) {
                // Single-column referrers (original path).
                val referee = reference.referee!!
                val refereeValue = with(thisRef) { referee.lookup() }

                val needsEntityIdUnwrap = reference.columnType !is EntityIDColumnType<*> &&
                    referee.columnType is EntityIDColumnType<*> && refereeValue is EntityID<*>

                @Suppress("UNCHECKED_CAST")
                val refValue = if (needsEntityIdUnwrap) refereeValue.value as REF else refereeValue as REF
                ;{
                    factory.find { reference eq refValue }
                        .orderBy(*orderByExpressions.toTypedArray())
                }
            } else {
                // Composite-FK referrers: build a compound AND of equalities, one per child→parent
                // column pair. Mirrors JDBC's composite branch in `Referrers.getValue`
                // (References.kt:149–156).
                val parentValuesByChildColumn = allReferences.map { (childColumn, parentColumn) ->
                    @Suppress("UNCHECKED_CAST")
                    val parentValueRaw = with(thisRef) { (parentColumn as Column<Any?>).lookup() }
                    // Unwrap EntityID when the child column stores a raw value.
                    val parentValue = if (parentValueRaw is EntityID<*> && childColumn.columnType !is EntityIDColumnType<*>) {
                        parentValueRaw._value
                    } else {
                        parentValueRaw
                    }
                    childColumn to parentValue
                }

                ;{
                    factory.find {
                        @Suppress("UNCHECKED_CAST")
                        parentValuesByChildColumn.map { (childColumn, value) ->
                            (childColumn as Column<Any?>) eq value
                        }.reduce { acc, next -> acc and next }
                    }.orderBy(*orderByExpressions.toTypedArray())
                }
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
