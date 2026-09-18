package org.jetbrains.exposed.v1.dao.r2dbc

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.emptySized
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KProperty

/**
 * What a many-to-many relationship declared with `via` gives back: the entities linked to this one through
 * an intermediate table.
 *
 * ```kotlin
 * class Film(id: EntityID<Int>) : IntEntity(id) {
 *     var actors by Actor via FilmActors
 *
 *     companion object : IntEntityClass<Film>(Films)
 * }
 *
 * film.actors = SizedCollection(actor1, actor2) // replaces the rows in FilmActors
 * val cast = film.actors.toList()
 * ```
 *
 * Unlike the other relationships, this property is a `var`: assigning a collection rewrites the links.
 *
 * @param table The intermediate table containing reference columns to both child and parent entities.
 * @param sourceTable The [IdTable] associated with the source child entity.
 * @param target The [EntityClass] for the target parent entity.
 * @param _sourceColumn The intermediate table's reference column for the child entity class. If left `null`,
 * this will be inferred from the provided intermediate [table] columns.
 * @param _targetColumn The intermediate table's reference column for the parent entity class. If left `null`,
 * this will be inferred from the provided intermediate [table] columns.
 */
@Suppress("UNCHECKED_CAST")
@ExperimentalR2dbcDaoApi
class InnerTableLink<SID : Any, Source : Entity<SID>, ID : Any, Target : Entity<ID>>(
    val table: Table,
    sourceTable: IdTable<SID>,
    val target: EntityClass<ID, Target>,
    _sourceColumn: Column<EntityID<SID>>? = null,
    _targetColumn: Column<EntityID<ID>>? = null,
) {
    private val orderByExpressions: MutableList<Pair<Expression<*>, SortOrder>> = mutableListOf()

    init {
        _targetColumn?.let {
            requireNotNull(_sourceColumn) { "Both source and target columns should be specified" }
            require(_targetColumn.referee?.table == target.table) {
                "Column $_targetColumn point to wrong table, expected ${target.table.tableName}"
            }
            require(_targetColumn.table == _sourceColumn.table) {
                "Both source and target columns should be from the same table"
            }
        }
        _sourceColumn?.let {
            requireNotNull(_targetColumn) { "Both source and target columns should be specified" }
            require(_sourceColumn.referee?.table == sourceTable) {
                "Column $_sourceColumn point to wrong table, expected ${sourceTable.tableName}"
            }
        }
    }

    /** The reference identity column for the child entity class. */
    val sourceColumn: Column<EntityID<SID>> = _sourceColumn
        ?: table.columns.singleOrNull { it.referee == sourceTable.id } as? Column<EntityID<SID>>
        ?: error("Table does not reference source")

    /** The reference identity column for the parent entity class. */
    val targetColumn: Column<EntityID<ID>> = _targetColumn
        ?: table.columns.singleOrNull { it.referee == target.table.id } as? Column<EntityID<ID>>
        ?: error("Table does not reference target")

    internal val columnsAndTables by lazy {
        val alreadyInJoin = (target.dependsOnTables as? Join)?.alreadyInJoin(table) ?: false
        val entityTables = if (alreadyInJoin) {
            target.dependsOnTables
        } else {
            target.dependsOnTables.join(table, JoinType.INNER, target.table.id, targetColumn)
        }
        val columns = (target.dependsOnColumns + (if (!alreadyInJoin) table.columns else emptyList()) - sourceColumn)
            .distinct() + sourceColumn
        columns to entityTables
    }

    internal fun orderByExpressionsArray(): Array<Pair<Expression<*>, SortOrder>> = orderByExpressions.toTypedArray()

    /**
     * Provides the property delegate by binding this link to the source [thisRef] and returning a
     * suspending [InnerTableLinkAccessor]. The accessor itself implements [SizedIterable] so that
     * `entity.targets` can be iterated as a Flow.
     */
    operator fun provideDelegate(
        thisRef: Source,
        property: KProperty<*>
    ): InnerTableLinkAccessor<SID, Source, ID, Target> = InnerTableLinkAccessor(this, thisRef)

    /** Modifies this reference to sort entities based on multiple columns as specified in [order]. */
    infix fun orderBy(order: List<Pair<Expression<*>, SortOrder>>) = this.also {
        orderByExpressions.addAll(order)
    }

    /** Modifies this reference to sort entities according to the specified [order]. */
    infix fun orderBy(order: Pair<Expression<*>, SortOrder>) = orderBy(listOf(order))

    /** Modifies this reference to sort entities by a column specified in [expression] using ascending order. */
    infix fun orderBy(expression: Expression<*>) = orderBy(listOf(expression to SortOrder.ASC))
}

/**
 * Property delegate companion to [InnerTableLink] — implements [SizedIterable] over the linked targets
 * of [entity] via the join table defined by [link]. R2DBC-specific because the iteration is built
 * around `Flow.collect`, whereas the JDBC implementation uses blocking iteration directly on
 * `InnerTableLink`.
 *
 * Assignment (`entity.targets = SizedCollection(values)`) is captured into the entity cache's
 * `pendingInnerTableLinkUpdates` and replayed during the next [EntityCache.flush] — this keeps the
 * write surface non-suspending so it can be used inside property setters.
 *
 * [SizedIterable] methods are delegated to an internal [DeferredQuery] so the iteration logic is
 * defined in one place ([doQuery]) and chained operations (`limit`, `offset`, `orderBy`) reuse the
 * same lazy-execution machinery.
 */
@Suppress("UNCHECKED_CAST")
@ExperimentalR2dbcDaoApi
class InnerTableLinkAccessor<SID : Any, Source : Entity<SID>, ID : Any, Target : Entity<ID>>(
    val link: InnerTableLink<SID, Source, ID, Target>,
    val entity: Source
) : SizedIterable<Target> by DeferredQuery({ doQuery(link, entity) }) {

    operator fun getValue(thisRef: Source, property: KProperty<*>): SizedIterable<Target> = this

    operator fun setValue(thisRef: Source, property: KProperty<*>, value: SizedIterable<Target>) {
        val entityCache = TransactionManager.current().entityCache
        entityCache.pendingInnerTableLinkUpdates.add {
            setReference(link, entity, value)
        }
    }

    override fun copy(): SizedIterable<Target> = InnerTableLinkAccessor(link, entity)
}

/**
 * Resolves linked targets from the entity's reference cache, for reads with no transaction in context.
 *
 * The cache is only populated when `keepLoadedReferencesOutOfTransaction` is enabled, so a miss is a
 * usage error and is reported as one. Returning the raw cache entry instead would hand back `null`
 * typed as a non-null [SizedIterable] and surface later as an unrelated `NullPointerException`.
 * Mirrors the equivalent branch in `Referrers`.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> Entity<*>.cachedLinkedTargets(sourceColumn: Column<*>): SizedIterable<T> {
    if (!hasInReferenceCache(sourceColumn)) {
        error(
            "Linked entities for $sourceColumn are not in the entity cache. Reading a `via` relation " +
                "without a transaction in context requires DatabaseConfig.keepLoadedReferencesOutOfTransaction " +
                "to be enabled, and the relation to have been loaded earlier inside a transaction."
        )
    }
    return when (val cached = getReferenceFromCache<Any?>(sourceColumn)) {
        is SizedIterable<*> -> cached as SizedIterable<T>
        null -> emptySized()
        else -> error("Cached linked entities have unexpected type: ${cached::class}")
    }
}

private suspend fun <SID : Any, Source : Entity<SID>, ID : Any, Target : Entity<ID>> doQuery(
    link: InnerTableLink<SID, Source, ID, Target>,
    entity: Source
): SizedIterable<Target> {
    if (entity.id._value == null && !entity.isNewEntity()) return emptySized()
    val transaction = TransactionManager.currentOrNull()
        ?: return entity.cachedLinkedTargets(link.sourceColumn)

    if (entity.id._value == null) {
        transaction.entityCache.flush()
    }

    val (columns, entityTables) = link.columnsAndTables

    val query: suspend () -> SizedIterable<Target> = {
        @Suppress("SpreadOperator")
        link.target.wrapRows(
            entityTables.select(columns)
                .where { link.sourceColumn eq entity.id }
                .orderBy(*link.orderByExpressionsArray())
        )
    }
    return transaction.entityCache.getOrPutReferrers<ID, Target>(entity.id, link.sourceColumn, query).also {
        entity.storeReferenceInCache(link.sourceColumn, it)
    }
}

@Suppress("UNCHECKED_CAST")
private suspend fun <SID : Any, Source : Entity<SID>, ID : Any, Target : Entity<ID>> setReference(
    link: InnerTableLink<SID, Source, ID, Target>,
    entity: Source,
    value: SizedIterable<Target>
) {
    val tx = TransactionManager.current()
    val entityCache = tx.entityCache
    val valueList = value.toList()
    val oldValue = doQuery(link, entity).toList()
    val existingIds = oldValue.map { it.id }.toSet()
    entityCache.referrers[link.sourceColumn]?.remove(entity.id)

    val targetIds = valueList.map { it.id }
    executeAsPartOfEntityLifecycle {
        link.table.deleteWhere { (link.sourceColumn eq entity.id) and (link.targetColumn notInList targetIds) }
        link.table.batchInsert(
            targetIds.filter { it !in existingIds },
            shouldReturnGeneratedValues = false
        ) { targetId ->
            this[link.sourceColumn] = entity.id
            this[link.targetColumn] = targetId
        }
    }

    tx.registerChange(entity.klass as EntityClass<*, Entity<*>>, entity.id, EntityChangeType.Updated)

    val targetClass = (valueList.firstOrNull() ?: oldValue.firstOrNull())?.klass
    if (targetClass != null) {
        (existingIds + targetIds).forEach { targetId ->
            tx.registerChange(targetClass as EntityClass<*, Entity<*>>, targetId, EntityChangeType.Updated)
        }
    }
}
