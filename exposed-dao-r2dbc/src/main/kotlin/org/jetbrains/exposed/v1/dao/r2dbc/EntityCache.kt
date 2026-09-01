package org.jetbrains.exposed.v1.dao.r2dbc

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.r2dbc.LazySizedCollection
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

private val entityCacheKey = Key<EntityCache>()

/**
 * The [EntityCache] belonging to the transaction, created on first access.
 */
@ExperimentalR2dbcDaoApi
val R2dbcTransaction.entityCache: EntityCache
    get() = getOrCreate(entityCacheKey) { EntityCache(this) }

/**
 * Class responsible for the storage of [Entity] instances in a specific [transaction].
 */
@ExperimentalR2dbcDaoApi
class EntityCache(private val transaction: R2dbcTransaction) {
    private val identityMap = ConcurrentHashMap<IdTable<*>, MutableMap<Any, Entity<*>>>()

    /**
     * The mapping of [IdTable]s to associated [Entity] instances (as a mapping of entity id values to entities).
     *
     * Owned by the session and stored inside root transaction.
     */
    val data: ConcurrentHashMap<IdTable<*>, MutableMap<Any, Entity<*>>>
        get() = sessionScope.identityMap

    @Volatile
    private var flushingEntities = false

    internal val inserts = ConcurrentHashMap<IdTable<*>, MutableSet<Entity<*>>>()

    internal val updates = ConcurrentHashMap<IdTable<*>, MutableSet<Entity<*>>>()

    internal val referrers = ConcurrentHashMap<Column<*>, MutableMap<EntityID<*>, SizedIterable<*>>>()

    /** Link writes queued because a `via` setter cannot suspend, and drained on flush. */
    internal val pendingInnerTableLinkUpdates = mutableListOf<suspend () -> Unit>()

    /** Uncommitted column values, kept off the entity so a rollback drops exactly what this scope staged. */
    private val staged = IdentityHashMap<Entity<*>, StagedValues>()

    /** Entities whose row this transaction created; a rollback drops the row and evicts the instance. */
    private val createdInScope = LinkedIdentityHashSet<Entity<*>>()

    /** The enclosing scope, which a savepoint-nested transaction reads through to. */
    private val outerScope: EntityCache?
        get() = transaction.outerTransaction?.entityCache

    /** This scope and its enclosing ones, innermost first. */
    private val scopeChain: Sequence<EntityCache>
        get() = generateSequence(this) { it.outerScope }

    /** The top-level transaction's cache, which owns everything the chain shares. */
    private val sessionScope: EntityCache by lazy { outerScope?.sessionScope ?: this }

    /**
     * Searches this [EntityCache] for an [Entity] by its [EntityID] value using its associated [EntityClass] as the key.
     *
     * @return The entity that has this wrapped id value, or `null` if no entity was found.
     */
    fun <ID : Any, T : Entity<ID>> find(f: EntityClass<ID, T>, id: EntityID<ID>): T? =
        // Mirrors JDBC's `EntityCache.find`. Unlike JDBC we can't dereference `id.value` blindly
        // (it would throw on an un-flushed entity), so the first lookup is gated by `id._value`.
        (id._value?.let { getMap(f.table)[it] as T? })
            ?: scopeChain.firstNotNullOfOrNull { scope ->
                scope.inserts[f.table]?.firstOrNull { it.id == id } as? T
                    ?: scope.initializingEntities.firstOrNull { it.klass == f && it.id == id } as? T
            }

    private fun getMap(table: IdTable<*>): MutableMap<Any, Entity<*>> = data.getOrPut(table) {
        LinkedHashMap()
    }

    /** Removes [entity] only if it is the instance currently mapped; another may have taken its place. */
    private fun removeFromIdentityMap(entity: Entity<*>) {
        val id = entity.id._value ?: return
        val map = data[entity.klass.table] ?: return
        if (map[id] === entity) map.remove(id)
    }

    /**
     * Stores the specified [Entity] in this cache.
     *
     * The [EntityClass] associated with this entity is inferred from its [Entity.klass] property.
     */
    fun store(o: Entity<*>) {
        getMap(o.klass.table)[o.id.value] = o
    }

    /**
     * [entity]'s staged value for [column], or [NoStagedValue] if no scope in the chain holds one.
     * The innermost scope wins, and within a scope an unissued assignment beats an issued one.
     */
    internal fun stagedValue(entity: Entity<*>, column: Column<Any?>): Any? {
        var scope: EntityCache? = this
        while (scope != null) {
            val values = scope.staged[entity]
            if (values != null) {
                val staged = values.valueOrNone(column)
                if (staged !== NoStagedValue) return staged
            }
            scope = scope.outerScope
        }
        return NoStagedValue
    }

    /** Records [value] as [entity]'s value for [column], to be sent to the database on the next flush. */
    internal fun stageWrite(entity: Entity<*>, column: Column<Any?>, value: Any?) {
        if (column.referee != null) {
            val stagedPrevious = stagedValue(entity, column)
            val previous = if (stagedPrevious === NoStagedValue) entity._readValues?.getOrNull(column) else stagedPrevious

            val staleParents = listOfNotNull(value, previous)
                .filterIsInstance<EntityID<*>>()
                .filter { it._value != null }

            for (scope in scopeChain) {
                val columnReferrers = scope.referrers[column] ?: continue
                staleParents.forEach { columnReferrers.remove(it) }
            }
        }

        staged.acquire(entity, transaction).dirty[column] = value
    }

    internal fun isDirty(entity: Entity<*>, column: Column<Any?>): Boolean =
        staged[entity]?.dirty?.containsKey(column) == true

    internal fun dirtyValues(entity: Entity<*>): Map<Column<Any?>, Any?> = staged[entity]?.dirty.orEmpty()

    internal fun markFlushed(entity: Entity<*>) {
        staged[entity]?.let {
            it.flushed.putAll(it.dirty)
            it.dirty.clear()
        }
    }

    internal fun discardDirty(entity: Entity<*>) {
        staged[entity]?.dirty?.clear()
    }

    internal fun forget(entity: Entity<*>) {
        staged.release(entity)
        createdInScope.remove(entity)
    }

    /**
     * Stops tracking [entity]: reads fall back to its committed values and writes throw. Safe to call twice.
     *
     * @throws IllegalStateException if it holds uncommitted values and [force] is `false`, or its row was
     *   created by a transaction that is still open.
     */
    internal fun detach(entity: Entity<*>, force: Boolean) {
        check(!wasCreatedInScope(entity)) {
            "Cannot detach ${entity.id}: its row was created by a transaction that is still open, which owns " +
                "whether that row comes to exist at all. Use delete() to withdraw it instead."
        }
        check(force || !holdsUncommittedValues(entity)) {
            "Cannot detach ${entity.id}: it holds values that have not been committed, and detaching would " +
                "drop them. Commit first, or pass `force = true` to discard them."
        }

        removeFromIdentityMap(entity)
        for (scope in scopeChain) {
            scope.forget(entity)
            scope.updates[entity.klass.table]?.remove(entity)
        }
    }

    private fun markCreatedInTheCurrentScope(entity: Entity<*>) {
        createdInScope.add(entity)
    }

    private fun holdsUncommittedValues(entity: Entity<*>): Boolean =
        scopeChain.any { entity in it.staged }

    private fun wasCreatedInScope(entity: Entity<*>): Boolean =
        scopeChain.any { entity in it.createdInScope }

    /**
     * Takes a freshly fetched [row] into this scope's staged values when [entity] already holds uncommitted
     * ones, since the row then reflects writes no transaction has committed. Reports whether it did.
     */
    internal fun stageFreshRow(entity: Entity<*>, row: ResultRow): Boolean {
        if (!holdsUncommittedValues(entity)) return false

        staged.acquire(entity, transaction).stageRow(row)
        return true
    }

    internal fun promoteUncommittedState() {
        val outer = outerScope
        if (outer != null) {
            staged.forEach { (entity, values) ->
                val target = outer.staged.acquire(entity, outer.transaction)

                // Staged value from inner transaction should overwrite dirty values from outer transactions
                val superseded = values.flushed.keys + values.dirty.keys
                outer.scopeChain.forEach { it.staged[entity]?.discardUnissued(superseded) }

                target.flushed.putAll(values.flushed)
                target.dirty.putAll(values.dirty)
            }
            outer.createdInScope.addAll(createdInScope)
        } else {
            staged.forEach { (entity, values) -> entity.updateByCommittedValues(values.flushed + values.dirty) }
        }
        staged.releaseAll()
        createdInScope.clear()
    }

    internal fun discardUncommittedState() {
        createdInScope.forEach { entity ->
            removeFromIdentityMap(entity)
        }
        staged.releaseAll()
        createdInScope.clear()
    }

    /** Removes the specified [Entity] from this [EntityCache] using its associated [table] as the key. */
    fun <ID : Any, T : Entity<ID>> remove(table: IdTable<ID>, o: T) {
        o.id._value?.let { getMap(table).remove(it) }
    }

    /** Stores the specified [Entity] in this [EntityCache] as scheduled to be updated in the database. */
    fun <ID : Any> scheduleUpdate(klass: EntityClass<ID, Entity<ID>>, entity: Entity<ID>) {
        updates.getOrPut(klass.table) { LinkedIdentityHashSet() }.add(entity)
    }

    /** Gets all [Entity] instances in this [EntityCache] that match the associated [EntityClass]. */
    fun <ID : Any, T : Entity<ID>> findAll(entityClass: EntityClass<ID, T>): List<T> {
        val map = data[entityClass.table] ?: return emptyList()
        return map.values.toList() as List<T>
    }

    private val initializingEntities = LinkedIdentityHashSet<Entity<*>>()

    internal fun <ID : Any> isEntityInInitializationState(entity: Entity<ID>): Boolean =
        scopeChain.any { entity in it.initializingEntities }

    internal fun <ID : Any> isScheduledForInsert(entity: Entity<ID>): Boolean =
        scopeChain.any { it.inserts[entity.klass.table]?.contains(entity) == true }

    internal fun <ID : Any> isStoredInData(entity: Entity<ID>): Boolean {
        val value = entity.id._value ?: return false
        return data[entity.klass.table]?.get(value) === entity
    }

    internal fun <ID : Any> addNotInitializedEntityToQueue(entity: Entity<ID>) {
        require(initializingEntities.add(entity)) { "Entity ${entity::class.simpleName} already in initialization process" }
    }

    internal fun <ID : Any> finishEntityInitialization(entity: Entity<ID>) {
        require(initializingEntities.lastOrNull() == entity) {
            "Can't finish initialization for entity ${entity::class.simpleName} - the initialization order is broken"
        }
        initializingEntities.remove(entity)
    }

    /** Stores the specified [Entity] in this [EntityCache] as scheduled to be inserted into the database. */
    fun <ID : Any> scheduleInsert(klass: EntityClass<ID, Entity<ID>>, entity: Entity<ID>) {
        markCreatedInTheCurrentScope(entity)
        inserts.getOrPut(klass.table) { LinkedIdentityHashSet() }.add(entity)
    }

    /**
     * Returns a [SizedIterable] containing all child [Entity] instances that reference the parent entity with
     * the provided [sourceId] using the specified [key] column.
     *
     * If either the [key] column is not present or a value does not exist for the parent entity, the default [refs]
     * will be called and its result will be put into the map under the given keys and the call result returned.
     */
    suspend fun <ID : Any, R : Entity<ID>> getOrPutReferrers(
        sourceId: EntityID<*>,
        key: Column<*>,
        refs: suspend () -> SizedIterable<@UnsafeVariance R>
    ): SizedIterable<R> {
        val columnReferrers = referrers.getOrPut(key) { ConcurrentHashMap() }
        @Suppress("UNCHECKED_CAST")
        return columnReferrers.getOrPut(sourceId) { LazySizedCollection(refs()) } as SizedIterable<R>
    }

    /**
     * Returns a [SizedIterable] containing all child [Entity] instances that reference the parent entity with
     * the provided [sourceId] using the specified [key] column.
     */
    fun <R : Entity<*>> getReferrers(sourceId: EntityID<*>, key: Column<*>): SizedIterable<R>? {
        @Suppress("UNCHECKED_CAST")
        return referrers[key]?.get(sourceId) as? SizedIterable<R>
    }

    /**
     * Clears this [EntityCache] of all stored data, including any reference mappings.
     *
     * @param flush By default, pending inserts and updates for all cached entities will first be sent to the
     * database. If this is set to `false`, any pending operations will not be flushed and will be removed as well.
     */
    suspend fun clear(flush: Boolean = true) {
        if (flush) flush()
        data.clear()
        inserts.clear()
        updates.clear()
        staged.releaseAll()
        createdInScope.clear()
        pendingInnerTableLinkUpdates.clear()
        clearReferrersCache()
    }

    /** Clears this [EntityCache] of stored data that maps cached parent entities to their referencing child entities. */
    fun clearReferrersCache() {
        referrers.clear()
    }

    private suspend fun <ID : Any> updateEntities(table: IdTable<ID>) {
        val update = updates.remove(table) ?: return
        if (update.isEmpty()) return

        val updatedEntities = HashSet<Entity<*>>()
        val batch = EntityBatchUpdate(update.first().klass)

        for (entity in update) {
            if (entity.flush(batch)) {
                updatedEntities.add(entity)
            }
        }

        executeAsPartOfEntityLifecycle {
            batch.execute(transaction)
        }

        updatedEntities.forEach {
            transaction.registerChange(it.klass, it.id, EntityChangeType.Updated)
        }
    }

    /** Sends all pending inserts and updates for all [Entity] instances in this [EntityCache] to the database. */
    suspend fun flush() {
        if (inserts.isEmpty() && updates.isEmpty() && pendingInnerTableLinkUpdates.isEmpty()) return
        val toFlush = when {
            inserts.isNotEmpty() && updates.isNotEmpty() -> inserts.keys + updates.keys
            inserts.isNotEmpty() -> inserts.keys
            updates.isNotEmpty() -> updates.keys
            else -> emptyList()
        }
        flush(toFlush)
    }

    /**
     * Sends all pending inserts and updates for [Entity] instances in this [EntityCache] to the database.
     *
     * The only entities that will be flushed are those that can be associated with any of the specified [tables].
     */
    suspend fun flush(tables: Iterable<IdTable<*>>) {
        if (flushingEntities) return
        try {
            flushingEntities = true
            val insertedTables = inserts.keys

            val updateBeforeInsert = SchemaUtils.sortTablesByReferences(insertedTables).filterIsInstance<IdTable<*>>()
            updateBeforeInsert.forEach { updateEntities(it) }

            SchemaUtils.sortTablesByReferences(tables).filterIsInstance<IdTable<*>>().forEach { flushInserts(it) }

            val updateTheRestTables = tables - updateBeforeInsert.toSet()
            for (t in updateTheRestTables) {
                updateEntities(t)
            }

            if (insertedTables.isNotEmpty()) {
                removeTablesReferrers(insertedTables, true)
            }

            if (pendingInnerTableLinkUpdates.isNotEmpty()) {
                executePendingInnerTableLinkUpdates()
            }
        } finally {
            flushingEntities = false
        }
    }

    private suspend fun executePendingInnerTableLinkUpdates() {
        val remainingInserts = inserts.keys.toList()
        for (table in SchemaUtils.sortTablesByReferences(remainingInserts).filterIsInstance<IdTable<*>>()) {
            flushInserts(table)
        }
        val remainingUpdates = updates.keys.toList()
        for (table in remainingUpdates) {
            updateEntities(table)
        }

        val pending = pendingInnerTableLinkUpdates.toList()
        pendingInnerTableLinkUpdates.clear()
        for (op in pending) {
            op()
        }
    }

    /**
     * Drops cached referrer lists a write to [tables] may have invalidated, across the whole scope chain
     * since it shares one connection. Over-invalidating only costs a re-query.
     */
    internal fun removeTablesReferrers(tables: Collection<Table>, isInsert: Boolean) {
        val insertedTablesSet = tables.toSet()
        val columnsToInvalidate = tables.flatMapTo(hashSetOf()) { table ->
            table.columns.mapNotNull { column -> column.takeIf { it.referee != null } }
        }

        for (scope in scopeChain) {
            val scopeReferrers = scope.referrers

            columnsToInvalidate.forEach {
                scopeReferrers.remove(it)
            }

            scopeReferrers.keys.filter { refColumn ->
                when {
                    isInsert -> false
                    refColumn.referee?.table in insertedTablesSet -> true
                    refColumn.table.columns.any { it.referee?.table in tables } -> true
                    else -> false
                }
            }.forEach {
                scopeReferrers.remove(it)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal suspend fun <ID : Any> flushInserts(table: IdTable<ID>) {
        var entitiesToInsert = inserts.remove(table)?.toList().orEmpty()
        if (entitiesToInsert.isEmpty()) return

        while (entitiesToInsert.isNotEmpty()) {
            val (currentBatch, nextBatch) = partitionEntitiesForInsert(entitiesToInsert, table)
            entitiesToInsert = nextBatch

            // Snapshot writeValues before the batchInsert reads them, so we can merge
            // client-set values back into `_readValues` for drivers that only return
            // generated columns.
            val stagedSnapshots = currentBatch.map { LinkedHashMap(dirtyValues(it)) }

            // The Oracle R2DBC driver rejects a statement that both batches rows and asks for generated values
            // ("Batch execution returning generated values is not supported"), and the generated ids are exactly
            // what has to come back here, so on Oracle every row is sent as a statement of its own.
            val chunks = if (currentDialect is OracleDialect) currentBatch.map(::listOf) else listOf(currentBatch)

            val genRows = executeAsPartOfEntityLifecycle {
                chunks.flatMap { chunk ->
                    table.batchInsert(chunk) { entry ->
                        for ((c, v) in dirtyValues(entry)) {
                            this[c] = v
                        }
                    }
                }
            }

            val incompleteIds = currentBatch.mapIndexedNotNull { idx, entity ->
                val complete = adoptGeneratedValues(entity, genRows[idx], stagedSnapshots[idx], table)
                (entity.id as EntityID<ID>).takeIf { !complete }
            }

            // Whatever the driver did not return -- a database-side default, for one -- has to be read back.
            // The ids are known by now, so that is one query for the batch rather than one per row.
            val rereadRows = if (incompleteIds.isEmpty()) {
                emptyMap()
            } else {
                table.selectAll().where { table.id inList incompleteIds }.toList().associateBy { it[table.id] }
            }

            currentBatch.forEachIndexed { idx, entity ->
                val values = staged.acquire(entity, transaction)
                values.dirty.clear()
                values.stageRow(rereadRows[entity.id] ?: genRows[idx])

                store(entity)
                transaction.registerChange(entity.klass, entity.id, EntityChangeType.Created)
            }
        }

        transaction.alertSubscribers()
    }

    private fun partitionEntitiesForInsert(
        entities: List<Entity<*>>,
        table: IdTable<*>
    ): Pair<List<Entity<*>>, List<Entity<*>>> {
        fun isAwaitingRowOfSameTable(entity: Entity<*>) = dirtyValues(entity).any { (column, value) ->
            column.referee == table.id && value is EntityID<*> && value._value == null
        }

        val anchor = entities.firstOrNull { !isAwaitingRowOfSameTable(it) }
        checkNotNull(anchor) {
            "Cannot flush the inserts scheduled for ${table.tableName}: every one of them has a foreign key " +
                "to a row of that table that has not been inserted yet, so there is none to send first. " +
                "Either those references form a cycle, or one points at an entity whose insert was withdrawn."
        }
        val anchorColumns = dirtyValues(anchor).keys

        return entities.partition { entity ->
            !isAwaitingRowOfSameTable(entity) && dirtyValues(entity).keys == anchorColumns
        }
    }

    /**
     * @return whether the row is now complete; if it is not, the missing columns have to be read back.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <ID : Any> adoptGeneratedValues(
        entity: Entity<*>,
        resultRow: ResultRow,
        stagedSnapshot: Map<Column<Any?>, Any?>,
        table: IdTable<ID>
    ): Boolean {
        val entityId = entity.id as EntityID<ID>
        val generatedId = resultRow[table.id]
        if (entityId._value == null) {
            entityId._value = generatedId.value
            entity.writeIdColumnValue(entity.klass.table, generatedId)
        }

        for ((column, value) in unwrapColumnValues(stagedSnapshot)) {
            if (!resultRow.hasValue(column)) resultRow[column] = value
        }

        return table.columns.all { resultRow.hasValue(it) }
    }
}

/**
 * Sends all pending [Entity] inserts and updates stored in this transaction's [EntityCache] to the database.
 *
 * @return A list of all new entities that were stored as scheduled for insert.
 */
@ExperimentalR2dbcDaoApi
suspend fun R2dbcTransaction.flushCache(): List<Entity<*>> {
    with(entityCache) {
        val newEntities = inserts.flatMap { it.value }
        flush()
        return newEntities
    }
}
