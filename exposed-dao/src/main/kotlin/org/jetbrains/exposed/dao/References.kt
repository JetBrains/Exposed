package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.emptySized
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

private fun checkReference(reference: Column<*>, factoryTable: IdTable<*>) {
    val refColumn = reference.referee ?: error("Column $reference is not a reference")
    val targetTable = refColumn.table
    if (factoryTable != targetTable) {
        error("Column and factory point to different tables")
    }
}

class Reference<REF:Comparable<REF>, ID:Comparable<ID>, out Target : Entity<ID>> (val reference: Column<REF>, val factory: EntityClass<ID, Target>) {
    init {
        checkReference(reference, factory.table)
    }
}

class OptionalReference<REF:Comparable<REF>, ID:Comparable<ID>, out Target : Entity<ID>> (val reference: Column<REF?>, val factory: EntityClass<ID, Target>) {
    init {
        checkReference(reference, factory.table)
    }
}

internal class BackReference<ParentID:Comparable<ParentID>, out Parent: Entity<ParentID>, ChildID:Comparable<ChildID>, in Child: Entity<ChildID>, REF>
(reference: Column<REF>, factory: EntityClass<ParentID, Parent>) : ReadOnlyProperty<Child, Parent> {
    internal val delegate = Referrers<ChildID, Child, ParentID, Parent, REF>(reference, factory, true)

    override operator fun getValue(thisRef: Child, property: KProperty<*>) = delegate.getValue(thisRef.apply { thisRef.id.value }, property).single() // flush entity before to don't miss newly created entities
}

class OptionalBackReference<ParentID:Comparable<ParentID>, out Parent: Entity<ParentID>, ChildID:Comparable<ChildID>, in Child: Entity<ChildID>, REF>
(reference: Column<REF?>, factory: EntityClass<ParentID, Parent>) : ReadOnlyProperty<Child, Parent?> {
    internal val delegate = OptionalReferrers<ChildID, Child, ParentID, Parent, REF>(reference, factory, true)

    override operator fun getValue(thisRef: Child, property: KProperty<*>) = delegate.getValue(thisRef.apply { thisRef.id.value }, property).singleOrNull()  // flush entity before to don't miss newly created entities
}

class Referrers<ParentID:Comparable<ParentID>, in Parent: Entity<ParentID>, ChildID:Comparable<ChildID>, out Child: Entity<ChildID>, REF>
(val reference: Column<REF>, val factory: EntityClass<ChildID, Child>, val cache: Boolean) : ReadOnlyProperty<Parent, SizedIterable<Child>> {
    init {
        reference.referee ?: error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    override operator fun getValue(thisRef: Parent, property: KProperty<*>): SizedIterable<Child> {
        val value = thisRef.run { reference.referee<REF>()!!.lookup() }
        if (thisRef.id._value == null || value == null) return emptySized()

        val query = {factory.find{reference eq value }}
        return if (cache) TransactionManager.current().entityCache.getOrPutReferrers(thisRef.id, reference, query) else query()
    }
}

class OptionalReferrers<ParentID:Comparable<ParentID>, in Parent: Entity<ParentID>, ChildID:Comparable<ChildID>, out Child: Entity<ChildID>, REF>
(val reference: Column<REF?>, val factory: EntityClass<ChildID, Child>, val cache: Boolean) : ReadOnlyProperty<Parent, SizedIterable<Child>> {
    init {
        reference.referee ?: error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    override operator fun getValue(thisRef: Parent, property: KProperty<*>): SizedIterable<Child> {
        val value = thisRef.run { reference.referee<REF>()!!.lookup() }
        if (thisRef.id._value == null || value == null) return emptySized()

        val query = {factory.find{reference eq value }}
        return if (cache) TransactionManager.current().entityCache.getOrPutReferrers(thisRef.id, reference, query)  else query()
    }
}

private fun <SRC: Entity<*>> getReferenceObjectFromDelegatedProperty(entity: SRC, property: KProperty1<SRC, Any?>) : Any? {
    property.isAccessible   = true
    return property.getDelegate(entity)
}

private fun <SRC: Entity<*>> filterRelationsForEntity(entity: SRC, relations: Array<out KProperty1<out Entity<*>, Any?>>): Collection<KProperty1<SRC, Any?>> {
    val validMembers = entity::class.memberProperties
    return validMembers.filter { it in relations } as Collection<KProperty1<SRC, Any?>>
}

@Suppress("UNCHECKED_CAST")
private fun <ID: Comparable<ID>> List<Entity<ID>>.preloadRelations(vararg relations: KProperty1<out Entity<*>, Any?>,
                                                                   nodesVisited: MutableSet<EntityClass<*, *>> = mutableSetOf())  {
    val entity              = this.firstOrNull() ?: return
    if(nodesVisited.contains(entity.klass)) {
        return
    } else {
        nodesVisited.add(entity.klass)
    }

    val directRelations = filterRelationsForEntity(entity, relations)
    directRelations.forEach {
        when(val refObject = getReferenceObjectFromDelegatedProperty(entity, it)) {
            is Reference<*, *, *> -> {
                (refObject as Reference<Comparable<Comparable<*>>, *, Entity<*>>).reference.let { refColumn ->
                    this.map { it.run { refColumn.lookup() } }.takeIf { it.isNotEmpty() }?.let { refIds ->
                        refObject.factory.find { refColumn.referee<Comparable<Comparable<*>>>()!! inList refIds.distinct() }.toList()
                    }.orEmpty()
                }
            }
            is OptionalReference<*, *, *> -> {
                (refObject as OptionalReference<Comparable<Comparable<*>>, *, Entity<*>>).reference.let { refColumn ->
                    this.mapNotNull { it.run { refColumn.lookup() } }.takeIf { it.isNotEmpty() }?.let { refIds ->
                        refObject.factory.find { refColumn.referee<Comparable<Comparable<*>>>()!! inList refIds.distinct() }.toList()
                    }.orEmpty()
                }
            }
            is Referrers<*, *, *, *, *> -> {
                (refObject as Referrers<ID, Entity<ID>, *, Entity<*>, Any>).reference.let { refColumn ->
                    val refIds = this.map { it.run { refColumn.referee<Any>()!!.lookup() } }
                    refObject.factory.warmUpReferences(refIds, refColumn)
                }
            }
            is OptionalReferrers<*, *, *, *, *> -> {
                (refObject as OptionalReferrers<ID, Entity<ID>, *, Entity<*>, Any>).reference.let { refColumn ->
                    val refIds = this.mapNotNull { it.run { refColumn.referee<Any?>()!!.lookup() } }
                    refObject.factory.warmUpOptReferences(refIds, refColumn)
                }
            }
            is InnerTableLink<*, *, *, *> -> {
                refObject.target.warmUpLinkedReferences(this.map{ it.id }, refObject.table)
            }
            is BackReference<*, *, *, *, *> -> {
                (refObject.delegate as Referrers<ID, Entity<ID>, *, Entity<*>, Any>).reference.let { refColumn ->
                    val refIds = this.map { it.run { refColumn.referee<Any>()!!.lookup() } }
                    refObject.delegate.factory.warmUpReferences(refIds, refColumn)
                }
            }
            is OptionalBackReference<*, *, *, *, *> -> {
                (refObject.delegate as OptionalReferrers<ID, Entity<ID>, *, Entity<*>, Any>).reference.let { refColumn ->
                    val refIds = this.map { it.run { refColumn.referee<Any>()!!.lookup() } }
                    refObject.delegate.factory.warmUpOptReferences(refIds, refColumn)
                }
            }
            else -> error("Relation delegate has an unknown type")
        }
    }

    if(directRelations.isNotEmpty() && relations.size != directRelations.size) {
        val remainingRelations      = relations.toList() - directRelations
        directRelations.map { relationProperty ->
            val relationsToLoad = this.flatMap {
                when(val relation = (relationProperty as KProperty1<Entity<*>, *>).get(it)) {
                    is SizedIterable<*> -> relation.toList()
                    is Entity<*> -> listOf(relation)
                    null                -> listOf()
                    else                -> error("Unrecognised loaded relation")
                } as List<Entity<Int>>
            }.groupBy { it::class }

            relationsToLoad.forEach { (_, entities) ->
                entities.preloadRelations(*remainingRelations.toTypedArray() as Array<out KProperty1<Entity<*>, Any?>>, nodesVisited = nodesVisited)
            }
        }
    }
}


fun <SRCID : Comparable<SRCID>, SRC: Entity<SRCID>, REF : Entity<*>> List<SRC>
        .with(vararg relations: KProperty1<out REF, Any?>) : List<SRC>
        = this.apply {
    preloadRelations(*relations)
}

fun <SRCID : Comparable<SRCID>, SRC: Entity<SRCID>> SRC.load(vararg relations: KProperty1<out Entity<*>, Any?>) : SRC {
    listOf(this).with(*relations)
    return this
}

fun <SRCID : Comparable<SRCID>, SRC: Entity<SRCID>> SizedIterable<SRC>.with(vararg relations: KProperty1<out Entity<*>, Any?>) : List<SRC>
        = this.toList().with(*relations)
