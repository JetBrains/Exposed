package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transactionScope
import java.util.concurrent.CopyOnWriteArrayList

enum class EntityChangeType {
    Created,
    Updated,
    Removed;
}


data class EntityChange(val entityClass: EntityClass<*, Entity<*>>, val entityId: EntityID<*>, val changeType: EntityChangeType, val transactionId: String)

fun<ID: Comparable<ID>, T: Entity<ID>> EntityChange.toEntity() : T? = (entityClass as EntityClass<ID, T>).findById(entityId as EntityID<ID>)

fun<ID: Comparable<ID>,T: Entity<ID>> EntityChange.toEntity(klass: EntityClass<ID, T>) : T? {
    if (!entityClass.isAssignableTo(klass)) return null
    @Suppress("UNCHECKED_CAST")
    return toEntity<ID, T>()
}

private val Transaction.entityEvents : MutableList<EntityChange> by transactionScope { CopyOnWriteArrayList<EntityChange>() }
private val entitySubscribers = CopyOnWriteArrayList<(EntityChange) -> Unit>()

object EntityHook {
    fun subscribe(action: (EntityChange) -> Unit): (EntityChange) -> Unit {
        entitySubscribers.add(action)
        return action
    }

    fun unsubscribe(action: (EntityChange) -> Unit) {
        entitySubscribers.remove(action)
    }
}

fun Transaction.registerChange(entityClass: EntityClass<*, Entity<*>>, entityId: EntityID<*>, changeType: EntityChangeType) {
    EntityChange(entityClass, entityId, changeType, id).let {
        if (entityEvents.lastOrNull() != it) {
            entityEvents.add(it)
        }
    }
}

fun Transaction.alertSubscribers() {
    entityEvents.forEach { e ->
        entitySubscribers.forEach {
            it(e)
        }
    }
    entityEvents.clear()
}

fun Transaction.registeredChanges() = entityEvents.toList()

fun <T> withHook(action: (EntityChange) -> Unit, body: ()->T): T {
    EntityHook.subscribe(action)
    try {
        return body().apply {
            TransactionManager.current().commit()
        }
    }
    finally {
        EntityHook.unsubscribe(action)
    }
}