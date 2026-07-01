package org.jetbrains.exposed.r2dbc.dao.relationships

import org.jetbrains.exposed.r2dbc.dao.Entity
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi
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

/**
 * Class responsible for implementing property delegates of the read-only properties involved in a one-to-many
 * relation, which retrieves all child entities that reference the parent entity.
 *
 * R2DBC counterpart of JDBC's `Referrers` from `References.kt`. The property delegate returns a
 * [SizedIterable] backed by a [DeferredQuery] — iteration is suspended until terminal operations
 * (`collect`, `count`, etc.) are invoked.
 *
 * @param reference The reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the child entity that references the parent entity.
 * @param cache Whether loaded reference entities should be stored in the [org.jetbrains.exposed.r2dbc.dao.EntityCache].
 */
@ExperimentalR2dbcDaoApi
class Referrers<ParentID : Any, in Parent : Entity<ParentID>, ChildID : Any, out Child : Entity<ChildID>, REF>(
    val reference: Column<REF>,
    val factory: EntityClass<ChildID, Child>,
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
     */
    val allReferences: Map<Column<*>, Column<*>> = references ?: run {
        reference.referee ?: error("Column $reference is not a reference")
        if (factory.table != reference.table) {
            error("Column $reference and factory ${factory.table.tableName} point to different tables")
        }
        @Suppress("UNCHECKED_CAST")
        mapOf(reference as Column<*> to reference.referee!!)
    }

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Parent, property: KProperty<*>): SizedIterable<Child> {
        return DeferredQuery(base = {
            doQuery(
                reference,
                factory as EntityClass<ChildID, Entity<ChildID>>,
                cache,
                allReferences,
                getOrderByExpressions(),
                thisRef as Entity<ParentID>
            ) as SizedIterable<Child>
        })
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

@Suppress("UNCHECKED_CAST", "NestedBlockDepth", "SpreadOperator", "LongParameterList")
private suspend fun <ChildID : Any, Child : Entity<ChildID>, REF> doQuery(
    reference: Column<REF>,
    factory: EntityClass<ChildID, Child>,
    cache: Boolean,
    allReferences: Map<Column<*>, Column<*>>,
    orderByExpressions: Array<Pair<Expression<*>, SortOrder>>,
    entity: Entity<*>
): SizedIterable<Child> {
    val transaction = TransactionManager.currentOrNull()

    return if (transaction == null) {
        if (entity.id._value == null) {
            emptySized()
        } else if (entity.hasInReferenceCache(reference)) {
            val cached = entity.getReferenceFromCache<Any?>(reference)
            when (cached) {
                is SizedIterable<*> -> cached as SizedIterable<Child>
                null -> emptySized()
                else -> error("Cached referrer has unexpected type: ${cached::class}")
            }
        } else {
            error("No transaction in context, and referrers not in entity cache for $reference")
        }
    } else {
        if (entity.id._value == null) {
            transaction.entityCache.flush()
        }

        val isComposite = allReferences.size > 1 || allReferences.values.firstOrNull()?.table is CompositeIdTable
        val query: suspend () -> SizedIterable<Child> = if (!isComposite) {
            val referee = reference.referee!!
            val refereeValue = with(entity) { referee.lookup() }

            val needsEntityIdUnwrap = reference.columnType !is EntityIDColumnType<*> &&
                referee.columnType is EntityIDColumnType<*> && refereeValue is EntityID<*>

            val refValue = if (needsEntityIdUnwrap) refereeValue.value as REF else refereeValue as REF
            ; {
                factory.find { reference eq refValue }
                    .orderBy(*orderByExpressions)
            }
        } else {
            val parentValuesByChildColumn = allReferences.map { (childColumn, parentColumn) ->
                val parentValueRaw = with(entity) { (parentColumn as Column<Any?>).lookup() }
                val parentValue = if (parentValueRaw is EntityID<*> && childColumn.columnType !is EntityIDColumnType<*>) {
                    parentValueRaw._value
                } else {
                    parentValueRaw
                }
                childColumn to parentValue
            }

            ; {
                factory.find {
                    parentValuesByChildColumn.map { (childColumn, value) ->
                        (childColumn as Column<Any?>) eq value
                    }.reduce { acc, next -> acc and next }
                }.orderBy(*orderByExpressions)
            }
        }

        val result = if (cache) {
            transaction.entityCache.getOrPutReferrers(entity.id, reference, query)
        } else {
            query()
        }

        entity.storeReferenceInCache(reference, result)
        result
    }
}
