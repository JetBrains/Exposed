package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transactionScope
import java.util.concurrent.CopyOnWriteArrayList

enum class EntityChangeType {
    Created,
    Updated,
    Removed; // Not supported still

    fun merge(type: EntityChangeType): EntityChangeType {
        return when (this) {
            EntityChangeType.Created -> if (type == EntityChangeType.Removed) type else this
            EntityChangeType.Updated -> if (type != EntityChangeType.Updated) type else this
            EntityChangeType.Removed -> this
        }
    }
}


data class EntityChange<ID: Any>(val entityClass: EntityClass<ID, Entity<ID>>, val id: EntityID<ID>, var changeType: EntityChangeType)

fun<ID: Any> EntityChange<ID>.toEntity() : Entity<ID>? {
    return entityClass.findById(id)
}

fun<ID: Any,T: Entity<ID>> EntityChange<*>.toEntity(klass: EntityClass<ID, T>) : T? {
    if (!entityClass.isAssignableTo(klass)) return null
    @Suppress("UNCHECKED_CAST")
    return toEntity() as? T
}

object EntityHook {
    private val entitySubscribers = CopyOnWriteArrayList<(EntityChange<*>) -> Unit>()

    private val events by transactionScope { arrayListOf<EntityChange<*>>() }

    val registeredEvents: List<EntityChange<*>> get() = events.toList()

    fun subscribe (action: (EntityChange<*>) -> Unit): (EntityChange<*>) -> Unit {
        entitySubscribers.add(action)
        return action
    }

    fun unsubscribe (action: (EntityChange<*>) -> Unit) {
        entitySubscribers.remove(action)
    }

    fun registerChange(change: EntityChange<*>) {
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

fun <T> withHook(action: (EntityChange<*>) -> Unit, body: ()->T): T {
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