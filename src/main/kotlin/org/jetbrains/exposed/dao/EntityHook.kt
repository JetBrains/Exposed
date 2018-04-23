package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transactionScope
import java.util.concurrent.CopyOnWriteArrayList

enum class EntityChangeType {
    Created,
    Updated,
    Removed;
}


data class EntityChange(val entityClass: EntityClass<*, Entity<*>>, val id: EntityID<*>, var changeType: EntityChangeType)

fun<ID: Comparable<ID>, T: Entity<ID>> EntityChange.toEntity() : T? = (entityClass as EntityClass<ID, T>).findById(id as EntityID<ID>)

fun<ID: Comparable<ID>,T: Entity<ID>> EntityChange.toEntity(klass: EntityClass<ID, T>) : T? {
    if (!entityClass.isAssignableTo(klass)) return null
    @Suppress("UNCHECKED_CAST")
    return toEntity<ID, T>()
}

object EntityHook {
    private val entitySubscribers = CopyOnWriteArrayList<(EntityChange) -> Unit>()

    private val events by transactionScope { CopyOnWriteArrayList<EntityChange>() }

    val registeredEvents: List<EntityChange> get() = events.toList()

    fun subscribe (action: (EntityChange) -> Unit): (EntityChange) -> Unit {
        entitySubscribers.add(action)
        return action
    }

    fun unsubscribe (action: (EntityChange) -> Unit) {
        entitySubscribers.remove(action)
    }

    fun registerChange(change: EntityChange) {
        if (events.lastOrNull() != change) {
            events.add(change)
        }
    }

    fun alertSubscribers() {
        events.forEach { e ->
            entitySubscribers.forEach {
                it(e)
            }
        }
        events.clear()
    }
}

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