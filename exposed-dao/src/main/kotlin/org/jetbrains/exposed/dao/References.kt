package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.LazySizedIterable
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.emptySized
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
class Reference<REF : Comparable<REF>, ID : Comparable<ID>, out Target : Entity<ID>>(
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
class OptionalReference<REF : Comparable<REF>, ID : Comparable<ID>, out Target : Entity<ID>>(
    val reference: Column<REF?>,
    val factory: EntityClass<ID, Target>
) {
    init {
        checkReference(reference, factory.table)
    }
}

/**
 * Class responsible for implementing property delegates of the read-only properties involved in a table
 * relation between two [Entity] classes, which retrieves the child entity that references the parent entity.
 *
 * @param reference The reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the child entity that references the parent entity.
 */
internal class BackReference<ParentID : Comparable<ParentID>, out Parent : Entity<ParentID>, ChildID : Comparable<ChildID>, in Child : Entity<ChildID>, REF>(
    reference: Column<REF>,
    factory: EntityClass<ParentID, Parent>
) : ReadOnlyProperty<Child, Parent> {
    internal val delegate = Referrers<ChildID, Child, ParentID, Parent, REF>(reference, factory, true)

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
class OptionalBackReference<ParentID : Comparable<ParentID>, out Parent : Entity<ParentID>, ChildID : Comparable<ChildID>, in Child : Entity<ChildID>, REF>(
    reference: Column<REF?>,
    factory: EntityClass<ParentID, Parent>
) : ReadOnlyProperty<Child, Parent?> {
    internal val delegate = OptionalReferrers<ChildID, Child, ParentID, Parent, REF>(reference, factory, true)

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
class Referrers<ParentID : Comparable<ParentID>, in Parent : Entity<ParentID>, ChildID : Comparable<ChildID>, out Child : Entity<ChildID>, REF>(
    val reference: Column<REF>,
    val factory: EntityClass<ChildID, Child>,
    val cache: Boolean
) : ReadOnlyProperty<Parent, SizedIterable<Child>> {
    init {
        reference.referee ?: error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    override operator fun getValue(thisRef: Parent, property: KProperty<*>): SizedIterable<Child> {
        val value = thisRef.run { reference.referee<REF>()!!.lookup() }
        if (thisRef.id._value == null || value == null) return emptySized()

        val query = { factory.find { reference eq value } }
        val transaction = TransactionManager.currentOrNull()
        return when {
            transaction == null -> thisRef.getReferenceFromCache(reference)
            cache -> {
                transaction.entityCache.getOrPutReferrers(thisRef.id, reference, query).also {
                    thisRef.storeReferenceInCache(reference, it)
                }
            }
            else -> query()
        }
    }
}

/**
 * Class responsible for implementing property delegates of the read-only properties involved in an optional one-to-many
 * relation, which retrieves all child entities that optionally reference the parent entity.
 *
 * @param reference The nullable reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the child entity that optionally references the parent entity.
 * @param cache Whether loaded reference entities should be stored in the [EntityCache].
 */
class OptionalReferrers<ParentID : Comparable<ParentID>, in Parent : Entity<ParentID>, ChildID : Comparable<ChildID>, out Child : Entity<ChildID>, REF>(
    val reference: Column<REF?>,
    val factory: EntityClass<ChildID, Child>,
    val cache: Boolean
) : ReadOnlyProperty<Parent, SizedIterable<Child>> {
    init {
        reference.referee ?: error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    override operator fun getValue(thisRef: Parent, property: KProperty<*>): SizedIterable<Child> {
        val value = thisRef.run { reference.referee<REF>()!!.lookup() }
        if (thisRef.id._value == null || value == null) return emptySized()

        val query = { factory.find { reference eq value } }
        val transaction = TransactionManager.currentOrNull()
        return when {
            transaction == null -> thisRef.getReferenceFromCache(reference)
            cache -> {
                transaction.entityCache.getOrPutReferrers(thisRef.id, reference, query).also {
                    thisRef.storeReferenceInCache(reference, it)
                }
            }
            else -> query()
        }
    }
}

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

@Suppress("UNCHECKED_CAST", "NestedBlockDepth", "ComplexMethod")
private fun <ID : Comparable<ID>> List<Entity<ID>>.preloadRelations(
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

    val directRelations = filterRelationsForEntity(entity, relations)
    directRelations.forEach { prop ->
        when (val refObject = getReferenceObjectFromDelegatedProperty(entity, prop)) {
            is Reference<*, *, *> -> {
                (refObject as Reference<Comparable<Comparable<*>>, *, Entity<*>>).reference.let { refColumn ->
                    this.map { it.run { refColumn.lookup() } }.takeIf { it.isNotEmpty() }?.let { refIds ->
                        refObject.factory.find { refColumn.referee<Comparable<Comparable<*>>>()!! inList refIds.distinct() }.toList()
                    }.orEmpty()
                    storeReferenceCache(refColumn, prop)
                }
            }
            is OptionalReference<*, *, *> -> {
                (refObject as OptionalReference<Comparable<Comparable<*>>, *, Entity<*>>).reference.let { refColumn ->
                    this.mapNotNull { it.run { refColumn.lookup() } }.takeIf { it.isNotEmpty() }?.let { refIds ->
                        refObject.factory.find { refColumn.referee<Comparable<Comparable<*>>>()!! inList refIds.distinct() }.toList()
                    }.orEmpty()
                    storeReferenceCache(refColumn, prop)
                }
            }
            is Referrers<*, *, *, *, *> -> {
                (refObject as Referrers<ID, Entity<ID>, *, Entity<*>, Any>).reference.let { refColumn ->
                    val refIds = this.map { it.run { refColumn.referee<Any>()!!.lookup() } }
                    refObject.factory.warmUpReferences(refIds, refColumn)
                    storeReferenceCache(refColumn, prop)
                }
            }
            is OptionalReferrers<*, *, *, *, *> -> {
                (refObject as OptionalReferrers<ID, Entity<ID>, *, Entity<*>, Any>).reference.let { refColumn ->
                    val refIds = this.mapNotNull { it.run { refColumn.referee<Any?>()!!.lookup() } }
                    refObject.factory.warmUpOptReferences(refIds, refColumn)
                    storeReferenceCache(refColumn, prop)
                }
            }
            is InnerTableLink<*, *, *, *> -> {
                (refObject as InnerTableLink<ID, Entity<ID>, Comparable<Comparable<*>>, Entity<Comparable<Comparable<*>>>>).let { innerTableLink ->
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
                (refObject.delegate as Referrers<ID, Entity<ID>, *, Entity<*>, Any>).reference.let { refColumn ->
                    val refIds = this.map { it.run { refColumn.referee<Any>()!!.lookup() } }
                    refObject.delegate.factory.warmUpReferences(refIds, refColumn)
                    storeReferenceCache(refColumn, prop)
                }
            }
            is OptionalBackReference<*, *, *, *, *> -> {
                (refObject.delegate as OptionalReferrers<ID, Entity<ID>, *, Entity<*>, Any>).reference.let { refColumn ->
                    val refIds = this.map { it.run { refColumn.referee<Any>()!!.lookup() } }
                    refObject.delegate.factory.warmUpOptReferences(refIds, refColumn)
                    storeReferenceCache(refColumn, prop)
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
fun <SRCID : Comparable<SRCID>, SRC : Entity<SRCID>, REF : Entity<*>, L : Iterable<SRC>> L.with(vararg relations: KProperty1<out REF, Any?>): L {
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
fun <SRCID : Comparable<SRCID>, SRC : Entity<SRCID>> SRC.load(vararg relations: KProperty1<out Entity<*>, Any?>): SRC = apply {
    listOf(this).with(*relations)
}
