package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A mutable collection wrapper for many-to-many relationships that tracks pending additions and removals.
 * Changes are applied to the database during entity flush.
 *
 * @param source The source entity owning this collection.
 * @param link The [InnerTableLink] managing the relationship.
 */
internal class MutableReferenceCollection<SID : Any, Source : Entity<SID>, ID : Any, Target : Entity<ID>>(
    private val source: Source,
    private val link: InnerTableLink<SID, Source, ID, Target>
) : MutableSizedIterable<Target> {
    private var delegate: SizedIterable<Target>? = null
    private val pendingAdditions = mutableSetOf<EntityID<ID>>()
    private val pendingRemovals = mutableSetOf<EntityID<ID>>()
    private var clearPending = false

    /**
     * Refreshes the delegate from the database (without flushing pending changes).
     * Called when we need to reload the database state.
     */
    internal fun refreshDelegate() {
        delegate = link.fetchDelegateWithoutFlush(source)
    }

    /**
     * Gets the current delegate, refreshing it if necessary.
     */
    private fun getDelegate(): SizedIterable<Target> {
        if (delegate == null) {
            refreshDelegate()
        }
        return delegate!!
    }

    /**
     * Returns the current state combining the database state with pending changes.
     * This computes the in-memory view without flushing to the database.
     */
    private fun getCurrentState(): List<Target> {
        // If no pending changes, return the delegate as-is (preserves ordering)
        if (!hasPendingChanges()) {
            return getDelegate().toList()
        }

        val current = if (clearPending) {
            emptyList()
        } else {
            getDelegate().toList()
        }

        val currentIds = current.map { it.id }.toMutableSet()
        currentIds.removeAll(pendingRemovals)
        currentIds.addAll(pendingAdditions)

        if (currentIds.isEmpty()) {
            return emptyList()
        }

        // Fetch all entities and create a map for quick lookup
        val allEntities = link.target.find { link.target.table.id inList currentIds }.associateBy { it.id }

        // Preserve order: existing items first (in their original order), then newly added items
        val result = mutableListOf<Target>()

        // Add existing items that weren't removed (preserving their order)
        current.forEach { entity ->
            if (!pendingRemovals.contains(entity.id)) {
                result.add(entity)
            }
        }

        // Add newly added items
        pendingAdditions.forEach { id ->
            allEntities[id]?.let { result.add(it) }
        }

        return result
    }

    override fun add(element: Target): Boolean {
        val elementId = element.id
        return if (clearPending) {
            pendingAdditions.add(elementId)
        } else {
            val alreadyPresent = getDelegate().any { it.id == elementId }
            if (alreadyPresent && !pendingRemovals.contains(elementId)) {
                false
            } else {
                pendingRemovals.remove(elementId)
                pendingAdditions.add(elementId)
                true
            }
        }
    }

    override fun remove(element: Target): Boolean {
        val elementId = element.id
        return if (clearPending) {
            pendingAdditions.remove(elementId)
        } else {
            val currentlyPresent = getDelegate().any { it.id == elementId }
            if (!currentlyPresent && !pendingAdditions.contains(elementId)) {
                false
            } else {
                pendingAdditions.remove(elementId)
                pendingRemovals.add(elementId)
                true
            }
        }
    }

    override fun addAll(elements: Collection<Target>): Boolean {
        var modified = false
        elements.forEach { if (add(it)) modified = true }
        return modified
    }

    override fun removeAll(elements: Collection<Target>): Boolean {
        var modified = false
        elements.forEach { if (remove(it)) modified = true }
        return modified
    }

    override fun clear() {
        clearPending = true
        pendingAdditions.clear()
        pendingRemovals.clear()
    }

    override fun iterator(): Iterator<Target> = getCurrentState().iterator()

    override fun limit(count: Int): SizedIterable<Target> = SizedCollection(getCurrentState().take(count))

    override fun offset(start: Long): SizedIterable<Target> = if (start >= Int.MAX_VALUE) {
        EmptySizedIterable()
    } else {
        SizedCollection(getCurrentState().drop(start.toInt()))
    }

    override fun count(): Long = getCurrentState().size.toLong()

    override fun empty(): Boolean = getCurrentState().isEmpty()

    override fun copy(): SizedIterable<Target> = SizedCollection(getCurrentState())

    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<Target> {
        // Note: Ordering is handled by InnerTableLink's orderByExpressions
        return this
    }

    /**
     * Checks if there are any pending changes to be flushed.
     */
    internal fun hasPendingChanges(): Boolean {
        return clearPending || pendingAdditions.isNotEmpty() || pendingRemovals.isNotEmpty()
    }

    /**
     * Applies the pending changes to the database and clears the pending sets.
     */
    internal fun flush() {
        if (!hasPendingChanges()) return

        val targetIds = if (clearPending) {
            pendingAdditions.toList()
        } else {
            val current = getDelegate().map { it.id }.toMutableSet()
            current.removeAll(pendingRemovals)
            current.addAll(pendingAdditions)
            current.toList()
        }

        link.applyChanges(source, targetIds)

        // Clear pending changes
        clearPending = false
        pendingAdditions.clear()
        pendingRemovals.clear()

        // Refresh delegate to reflect the new state
        refreshDelegate()
    }
}

/**
 * Class responsible for implementing property delegates of the read-write properties involved in a many-to-many
 * relation, which uses an intermediate (join) table.
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
class InnerTableLink<SID : Any, Source : Entity<SID>, ID : Any, Target : Entity<ID>>(
    val table: Table,
    sourceTable: IdTable<SID>,
    val target: EntityClass<ID, Target>,
    _sourceColumn: Column<EntityID<SID>>? = null,
    _targetColumn: Column<EntityID<ID>>? = null,
) : ReadWriteProperty<Source, SizedIterable<Target>> {
    /** The list of columns and their [SortOrder] for ordering referred entities in many-to-many relationship. */
    private val orderByExpressions: MutableList<Pair<Expression<*>, SortOrder>> = mutableListOf()

    /** Cache of mutable collection instances per source entity. */
    private val mutableCollections = mutableMapOf<EntityID<SID>, MutableReferenceCollection<SID, Source, ID, Target>>()

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
    val sourceColumn = _sourceColumn
        ?: table.columns.singleOrNull { it.referee == sourceTable.id } as? Column<EntityID<SID>>
        ?: error("Table does not reference source")

    /** The reference identity column for the parent entity class. */
    val targetColumn = _targetColumn
        ?: table.columns.singleOrNull { it.referee == target.table.id } as? Column<EntityID<ID>>
        ?: error("Table does not reference target")

    private val columnsAndTables by lazy {
        val alreadyInJoin = (target.dependsOnTables as? Join)?.alreadyInJoin(table) ?: false
        val entityTables =
            if (alreadyInJoin) target.dependsOnTables else target.dependsOnTables.join(table, JoinType.INNER, target.table.id, targetColumn)

        val columns = (target.dependsOnColumns + (if (!alreadyInJoin) table.columns else emptyList()) - sourceColumn).distinct() + sourceColumn

        columns to entityTables
    }

    override operator fun getValue(o: Source, unused: KProperty<*>): MutableSizedIterable<Target> {
        if (o.id._value == null && !o.isNewEntity()) return MutableReferenceCollection(o, this)

        // Return cached mutable collection if it exists
        val cached = mutableCollections[o.id]
        if (cached != null) {
            return cached
        }

        // Create new mutable collection
        val mutableCollection = MutableReferenceCollection(o, this)
        mutableCollections[o.id] = mutableCollection
        return mutableCollection
    }

    /**
     * Fetches the current delegate (database state) for the given source entity without flushing.
     * Used to load the initial state or refresh after a flush.
     */
    internal fun fetchDelegateWithoutFlush(o: Source): SizedIterable<Target> {
        val transaction = TransactionManager.currentOrNull()
            ?: return o.getReferenceFromCache(sourceColumn)

        val (columns, entityTables) = columnsAndTables

        val query = {
            target.wrapRows(
                @Suppress("SpreadOperator")
                entityTables.select(columns)
                    .where { sourceColumn eq o.id }
                    .orderBy(*orderByExpressions.toTypedArray())
            )
        }
        return transaction.entityCache.getOrPutReferrers(o.id, sourceColumn, query).also {
            o.storeReferenceInCache(sourceColumn, it)
        }
    }

    /**
     * Flushes any pending changes for the given source entity.
     * Called before operations that need consistent database state.
     */
    internal fun flushPendingChanges(o: Source) {
        mutableCollections[o.id]?.flush()
    }

    override fun setValue(o: Source, unused: KProperty<*>, value: SizedIterable<Target>) {
        val entityCache = TransactionManager.current().entityCache
        if (entityCache.isEntityInInitializationState(o)) {
            entityCache.pendingInitializationLambdas.getOrPut(o) { arrayListOf() }.add {
                setReference(it as Source, value)
            }
        } else {
            setReference(o, value)
        }
    }

    private fun setReference(o: Source, value: SizedIterable<Target>) {
        val targetIds = value.map { it.id }
        applyChanges(o, targetIds)

        // Clear the cached mutable collection to force refresh
        mutableCollections.remove(o.id)
    }

    /**
     * Applies the specified target IDs to the relationship, updating the intermediate table.
     * Called by [setReference] when the collection is reassigned and by [MutableReferenceCollection.flush]
     * when pending changes are flushed.
     */
    internal fun applyChanges(o: Source, targetIds: List<EntityID<ID>>) {
        val tx = TransactionManager.current()
        val entityCache = tx.entityCache
        entityCache.flush()

        val oldValue = fetchDelegateWithoutFlush(o)
        val existingIds = oldValue.map { it.id }.toSet()
        entityCache.referrers[sourceColumn]?.remove(o.id)

        executeAsPartOfEntityLifecycle {
            table.deleteWhere { (sourceColumn eq o.id) and (targetColumn notInList targetIds) }
            table.batchInsert(targetIds.filter { !existingIds.contains(it) }, shouldReturnGeneratedValues = false) { targetId ->
                this[sourceColumn] = o.id
                this[targetColumn] = targetId
            }
        }

        // current entity updated
        tx.registerChange(o.klass, o.id, EntityChangeType.Updated)

        // linked entities updated
        val targetClass = target
        existingIds.plus(targetIds).forEach {
            tx.registerChange(targetClass, it, EntityChangeType.Updated)
        }
    }

    /** Modifies this reference to sort entities based on multiple columns as specified in [order]. **/
    infix fun orderBy(order: List<Pair<Expression<*>, SortOrder>>) = this.also {
        orderByExpressions.addAll(order)
    }

    /** Modifies this reference to sort entities according to the specified [order]. **/
    infix fun orderBy(order: Pair<Expression<*>, SortOrder>) = orderBy(listOf(order))

    /** Modifies this reference to sort entities by a column specified in [expression] using ascending order. **/
    infix fun orderBy(expression: Expression<*>) = orderBy(listOf(expression to SortOrder.ASC))
}
