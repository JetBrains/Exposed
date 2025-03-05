package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.wrap
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

private fun checkReference(reference: Column<*>, factoryTable: IdTable<*>): Map<Column<*>, Column<*>> {
    val refColumn = reference.referee ?: error("Column $reference is not a reference")
    val targetTable = refColumn.table
    if (factoryTable != targetTable) {
        error("Column and factory point to different tables")
    }
    return mapOf(reference to refColumn)
}

/**
 * Class representing a table relation between two [Entity] classes, which is responsible for
 * retrieving the parent entity referenced by the child entity.
 *
 * @param reference The reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the parent entity referenced by the child entity.
 */
class Reference<REF : Any, ID : Any, out Target : Entity<ID>>(
    val reference: Column<REF>,
    val factory: EntityClass<ID, Target>,
    references: Map<Column<*>, Column<*>>? = null
) {
    val allReferences = references ?: checkReference(reference, factory.table)
}

/**
 * Class representing an optional table relation between two [Entity] classes, which is responsible for
 * retrieving the parent entity optionally referenced by the child entity.
 *
 * @param reference The nullable reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the parent entity optionally referenced by the child entity.
 */
class OptionalReference<REF : Any, ID : Any, out Target : Entity<ID>>(
    val reference: Column<REF?>,
    val factory: EntityClass<ID, Target>,
    references: Map<Column<*>, Column<*>>? = null
) {
    val allReferences = references ?: checkReference(reference, factory.table)
}

/**
 * Class responsible for implementing property delegates of the read-only properties involved in a table
 * relation between two [Entity] classes, which retrieves the child entity that references the parent entity.
 *
 * @param reference The reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the child entity that references the parent entity.
 */
internal class BackReference<ParentID : Any, out Parent : Entity<ParentID>, ChildID : Any, in Child : Entity<ChildID>, REF>(
    reference: Column<REF>,
    factory: EntityClass<ParentID, Parent>,
    references: Map<Column<*>, Column<*>>? = null
) : ReadOnlyProperty<Child, Parent> {
    internal val delegate = Referrers<ChildID, Child, ParentID, Parent, REF>(reference, factory, true, references)

    override operator fun getValue(thisRef: Child, property: KProperty<*>) =
        delegate.getValue(thisRef.apply { thisRef.id.value }, property).single() // flush entity before to don't miss newly created entities
}

/**
 * Class responsible for implementing property delegates of the read-only properties involved in an optional table
 * relation between two [Entity] classes, which retrieves the child entity that optionally references the parent entity.
 *
 * @param reference The nullable reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the child entity that optionally references the parent entity.
 */
class OptionalBackReference<ParentID : Any, out Parent : Entity<ParentID>, ChildID : Any, in Child : Entity<ChildID>, REF>(
    reference: Column<REF?>,
    factory: EntityClass<ParentID, Parent>,
    references: Map<Column<*>, Column<*>>? = null
) : ReadOnlyProperty<Child, Parent?> {
    internal val delegate = Referrers<ChildID, Child, ParentID, Parent, REF?>(reference, factory, true, references)

    override operator fun getValue(thisRef: Child, property: KProperty<*>) =
        delegate.getValue(thisRef.apply { thisRef.id.value }, property).singleOrNull() // flush entity before to don't miss newly created entities
}

/**
 * Class responsible for implementing property delegates of the read-only properties involved in a one-to-many
 * relation, which retrieves all child entities that reference the parent entity.
 *
 * @param reference The reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the child entity that references the parent entity.
 * @param cache Whether loaded reference entities should be stored in the [EntityCache].
 */
open class Referrers<ParentID : Any, in Parent : Entity<ParentID>, ChildID : Any, out Child : Entity<ChildID>, REF>(
    val reference: Column<REF>,
    val factory: EntityClass<ChildID, Child>,
    val cache: Boolean,
    references: Map<Column<*>, Column<*>>? = null
) : ReadOnlyProperty<Parent, SizedIterable<Child>> {
    /** The list of columns and their [SortOrder] for ordering referred entities in one-to-many relationship. */
    private val orderByExpressions: MutableList<Pair<Expression<*>, SortOrder>> = mutableListOf()

    val allReferences = references ?: run {
        reference.referee ?: error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }

        mapOf(reference as Column<*> to reference.referee!!)
    }

    @Suppress("UNCHECKED_CAST", "NestedBlockDepth")
    override operator fun getValue(thisRef: Parent, property: KProperty<*>): SizedIterable<Child> {
        val isSingleIdReference = hasSingleReferenceWithReferee(allReferences)
        val value: REF = thisRef.run {
            if (isSingleIdReference) {
                val refereeColumn = reference.referee<REF>()!!
                val refereeValue = refereeColumn.lookup()
                when {
                    reference.columnType !is EntityIDColumnType<*> && refereeColumn.columnType is EntityIDColumnType<*> ->
                        (refereeValue as? EntityID<*>)?.let { it.value as? REF } ?: refereeValue
                    else -> refereeValue
                }
            } else {
                getCompositeID {
                    allReferences.map { (_, parent) -> parent to parent.lookup() }
                } as REF
            }
        }
        if (thisRef.id._value == null || value == null) return emptySized()

        val condition = if (isSingleIdReference) {
            reference eq value
        } else {
            value as CompositeID
            allReferences.map { (child, parent) ->
                val parentValue = value[parent as Column<EntityID<Any>>].value
                EqOp(child, child.wrap((parentValue as? DaoEntityID<*>)?.value ?: parentValue))
            }.compoundAnd()
        }
        val query = {
            @Suppress("SpreadOperator")
            factory
                .find { condition }
                .orderBy(*orderByExpressions.toTypedArray())
        }
        val transaction = TransactionManager.currentOrNull()
        return when {
            transaction == null -> {
                val cachedValue = thisRef.getReferenceFromCache<Any?>(reference)
                when {
                    cachedValue is SizedIterable<*> -> cachedValue as SizedIterable<Child>
                    cachedValue != null -> LazySizedCollection(SizedCollection(cachedValue as Child))
                    thisRef.hasInReferenceCache(reference) ->
                        EmptySizedIterable() // actively cached the value `null` - so provide an empty value
                    else -> throw error("No transaction in context, and $reference not in entity cache.")
                }
            }
            cache -> {
                transaction.entityCache.getOrPutReferrers(thisRef.id, reference, query).also {
                    thisRef.storeReferenceInCache(reference, it)
                }
            }
            else -> query()
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

    /** Modifies this reference to sort entities based on multiple columns as specified in [order]. **/
    fun orderBy(vararg order: Pair<Expression<*>, SortOrder>) = orderBy(order.toList())
}

/**
 * Class responsible for implementing property delegates of the read-only properties involved in an optional one-to-many
 * relation, which retrieves all child entities that optionally reference the parent entity.
 *
 * @param reference The nullable reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the child entity that optionally references the parent entity.
 * @param cache Whether loaded reference entities should be stored in the [EntityCache].
 */
@Deprecated(
    message = "The OptionalReferrers class is a complete duplicate of the Referrers class; therefore, the latter should be used instead.",
    replaceWith = ReplaceWith("Referrers"),
    level = DeprecationLevel.ERROR
)
class OptionalReferrers<ParentID : Any, in Parent : Entity<ParentID>, ChildID : Any, out Child : Entity<ChildID>, REF>(
    reference: Column<REF?>,
    factory: EntityClass<ChildID, Child>,
    cache: Boolean,
    references: Map<Column<*>, Column<*>>? = null
) : Referrers<ParentID, Parent, ChildID, Child, REF?>(reference, factory, cache, references)

private fun <SRC : Entity<*>> getReferenceObjectFromDelegatedProperty(entity: SRC, property: KProperty1<SRC, Any?>): Any? {
    property.isAccessible = true
    return property.getDelegate(entity)
}

private fun <SRC : Entity<*>> filterRelationsForEntity(
    entity: SRC,
    relations: Array<out KProperty1<out Entity<*>, Any?>>
): Collection<KProperty1<SRC, Any?>> {
    val validMembers = entity::class.memberProperties
    return validMembers.filter { it in relations } as Collection<KProperty1<SRC, Any?>>
}

@Suppress("UNCHECKED_CAST", "NestedBlockDepth", "ComplexMethod", "LongMethod")
private fun <ID : Any> List<Entity<ID>>.preloadRelations(
    vararg relations: KProperty1<out Entity<*>, Any?>,
    nodesVisited: MutableSet<EntityClass<*, *>> = mutableSetOf()
) {
    val entity = this.firstOrNull() ?: return
    if (nodesVisited.contains(entity.klass)) {
        return
    } else {
        nodesVisited.add(entity.klass)
    }

    val isReferenceCacheEnabled = TransactionManager.currentOrNull()?.db?.config?.keepLoadedReferencesOutOfTransaction ?: false

    fun storeReferenceCache(reference: Column<*>, prop: KProperty1<Entity<ID>, Any?>) {
        if (isReferenceCacheEnabled) {
            this.forEach { entity ->
                entity.storeReferenceInCache(reference, prop.get(entity))
            }
        }
    }

    fun Entity<*>.getReferenceId(
        delegateRefColumn: Column<*>,
        refColumns: Map<Column<*>, Column<*>>,
        isSingleIdReference: Boolean
    ): Any? {
        return if (isSingleIdReference) {
            delegateRefColumn.lookup()
        } else {
            val childValues = refColumns.keys.map { it.lookup() }
            if (childValues.any { it == null }) {
                null
            } else {
                getCompositeID {
                    refColumns.values.mapIndexed { i, parent -> parent to childValues[i] }
                }
            }
        }
    }

    fun Entity<*>.getCompositeReferrerId(refColumns: Map<Column<*>, Column<*>>) = getCompositeID {
        refColumns.map { (child, parent) -> child to (parent.lookup() as EntityID<*>).value }
    }

    fun Entity<*>.getRefereeId(refereeColumn: Column<*>, delegateRefColumn: Column<*>): Any {
        val refereeValue = refereeColumn.lookup()
        return refereeValue.takeUnless {
            delegateRefColumn.columnType !is EntityIDColumnType<*> && it is EntityID<*>
        } ?: (refereeValue as EntityID<*>).value
    }

    val directRelations = filterRelationsForEntity(entity, relations)
    directRelations.forEach { prop ->
        when (val refObject = getReferenceObjectFromDelegatedProperty(entity, prop)) {
            is Reference<*, *, *> -> {
                (refObject as Reference<*, *, Entity<*>>).allReferences.let { refColumns ->
                    val isSingleIdReference = hasSingleReferenceWithReferee(refColumns)
                    val delegateRefColumn = refObject.reference
                    this.map { entity ->
                        entity.getReferenceId(delegateRefColumn, refColumns, isSingleIdReference) as ID
                    }.takeIf { it.isNotEmpty() }?.let { refIds ->
                        val condition = if (isSingleIdReference) {
                            val castReferee = (delegateRefColumn as Column<ID>).referee<ID>()!!
                            val baseReferee = castReferee.takeUnless {
                                it.columnType is EntityIDColumnType<*> && refIds.first() !is EntityID<*>
                            } ?: (castReferee.columnType as EntityIDColumnType<ID>).idColumn
                            baseReferee inList refIds.distinct()
                        } else {
                            refColumns.values.toList() inList (refIds.distinct() as List<CompositeID>)
                        }
                        refObject.factory.find(condition).toList()
                    }.orEmpty()
                    storeReferenceCache(delegateRefColumn, prop)
                }
            }
            is OptionalReference<*, *, *> -> {
                (refObject as OptionalReference<*, *, Entity<*>>).allReferences.let { refColumns ->
                    val isSingleIdReference = hasSingleReferenceWithReferee(refColumns)
                    val delegateRefColumn = refObject.reference
                    this.mapNotNull { entity ->
                        entity.getReferenceId(delegateRefColumn, refColumns, isSingleIdReference) as? ID
                    }.takeIf { it.isNotEmpty() }?.let { refIds ->
                        val condition = if (isSingleIdReference) {
                            (delegateRefColumn as Column<ID>).referee<ID>()!! inList refIds.distinct()
                        } else {
                            refColumns.values.toList() inList (refIds.distinct() as List<CompositeID>)
                        }
                        refObject.factory.find(condition).toList()
                    }.orEmpty()
                    storeReferenceCache(delegateRefColumn, prop)
                }
            }
            is Referrers<*, *, *, *, *> -> {
                (refObject as Referrers<ID, Entity<ID>, *, Entity<*>, Any>).allReferences.let { refColumns ->
                    val delegateRefColumn = refObject.reference
                    if (hasSingleReferenceWithReferee(refColumns)) {
                        val castReferee = delegateRefColumn.referee<Any>()!!
                        val refIds = this.map { entity -> entity.getRefereeId(castReferee, delegateRefColumn) }
                        refObject.factory.warmUpReferences(refIds, delegateRefColumn)
                    } else {
                        val refIds = this.map { it.getCompositeReferrerId(refColumns) }
                        refObject.factory.warmUpCompositeIdReferences(refIds, refColumns, delegateRefColumn)
                    }
                    storeReferenceCache(delegateRefColumn, prop)
                }
            }
            is InnerTableLink<*, *, *, *> -> {
                (refObject as InnerTableLink<ID, Entity<ID>, Any, Entity<Any>>).let { innerTableLink ->
                    innerTableLink.target.warmUpLinkedReferences(
                        references = this.map { it.id },
                        sourceRefColumn = innerTableLink.sourceColumn,
                        targetRefColumn = innerTableLink.targetColumn,
                        linkTable = innerTableLink.table
                    )
                    storeReferenceCache(innerTableLink.sourceColumn, prop)
                }
            }
            is BackReference<*, *, *, *, *> -> {
                (refObject.delegate as Referrers<ID, Entity<ID>, *, Entity<*>, Any>).allReferences.let { refColumns ->
                    val delegateRefColumn = refObject.delegate.reference
                    if (hasSingleReferenceWithReferee(refColumns)) {
                        val castReferee = delegateRefColumn.referee<Any>()!!
                        val refIds = this.map { entity -> entity.getRefereeId(castReferee, delegateRefColumn) }
                        refObject.delegate.factory.warmUpReferences(refIds, delegateRefColumn)
                    } else {
                        val refIds = this.map { it.getCompositeReferrerId(refColumns) }
                        refObject.delegate.factory.warmUpCompositeIdReferences(refIds, refColumns, delegateRefColumn)
                    }
                    storeReferenceCache(delegateRefColumn, prop)
                }
            }
            is OptionalBackReference<*, *, *, *, *> -> {
                (refObject.delegate as Referrers<ID, Entity<ID>, *, Entity<*>, Any?>).allReferences.let { refColumns ->
                    val delegateRefColumn = refObject.delegate.reference
                    if (hasSingleReferenceWithReferee(refColumns)) {
                        val refIds = this.map { it.run { delegateRefColumn.referee<Any>()!!.lookup() } }
                        refObject.delegate.factory.warmUpOptReferences(refIds, delegateRefColumn)
                    } else {
                        val refIds = this.map { it.getCompositeReferrerId(refColumns) }
                        refObject.delegate.factory.warmUpCompositeIdReferences(refIds, refColumns, delegateRefColumn)
                    }
                    storeReferenceCache(delegateRefColumn, prop)
                }
            }
            else -> error("Relation delegate has an unknown type")
        }
    }

    if (directRelations.isNotEmpty() && relations.size != directRelations.size) {
        val remainingRelations = relations.toList() - directRelations
        directRelations.map { relationProperty ->
            val relationsToLoad = this.flatMap {
                when (val relation = (relationProperty as KProperty1<Entity<*>, *>).get(it)) {
                    is SizedIterable<*> -> relation.toList()
                    is Entity<*> -> listOf(relation)
                    null -> listOf()
                    else -> error("Unrecognised loaded relation")
                } as List<Entity<Int>>
            }.groupBy { it::class }

            relationsToLoad.forEach { (_, entities) ->
                entities.preloadRelations(
                    relations = remainingRelations.toTypedArray() as Array<out KProperty1<Entity<*>, Any?>>,
                    nodesVisited = nodesVisited
                )
            }
        }
    }
}

/**
 * Eager loads references for all [Entity] instances in this collection and returns this collection.
 *
 * **See also:** [Eager Loading](https://github.com/JetBrains/Exposed/wiki/DAO#eager-loading)
 *
 * @param relations The reference fields of the entities, as [KProperty]s, which should be loaded.
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.preloadRelationAtDepth
 */
fun <SRCID : Any, SRC : Entity<SRCID>, REF : Entity<*>, L : Iterable<SRC>> L.with(vararg relations: KProperty1<out REF, Any?>): L {
    toList().apply {
        (this@with as? LazySizedIterable<SRC>)?.loadedResult = this
        if (any { it.isNewEntity() }) {
            TransactionManager.current().flushCache()
        }
        preloadRelations(*relations)
    }
    return this
}

/**
 * Eager loads references for this [Entity] instance and returns this entity instance.
 *
 * **See also:** [Eager Loading](https://github.com/JetBrains/Exposed/wiki/DAO#eager-loading)
 *
 * @param relations The reference fields of this entity, as [KProperty]s, which should be loaded.
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.preloadOptionalReferencesOnAnEntity
 */
fun <SRCID : Any, SRC : Entity<SRCID>> SRC.load(vararg relations: KProperty1<out Entity<*>, Any?>): SRC = apply {
    listOf(this).with(*relations)
}

internal fun hasSingleReferenceWithReferee(allReferences: Map<Column<*>, Column<*>>?): Boolean {
    return allReferences?.size == 1 && allReferences.values.first().table !is CompositeIdTable
}

internal fun allReferencesMatch(allReferences: Map<Column<*>, Column<*>>, parentTable: IdTable<*>): Boolean {
    val parentIdColumns = parentTable.idColumns
    return allReferences.values.size == parentIdColumns.size && allReferences.values.containsAll(parentIdColumns)
}

@Suppress("UNCHECKED_CAST")
internal fun getCompositeID(entries: () -> List<Pair<Column<*>, *>>): CompositeID = CompositeID {
    entries().forEach { (key, value) ->
        it[key as Column<EntityID<Any>>] = value as Any
    }
}
