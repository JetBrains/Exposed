package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
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

    fun subscribe (action: (EntityChange) -> Unit): (EntityChange) -> Unit {
        entitySubscribers.add(action)
        return action
    }

    fun unsubscribe (action: (EntityChange) -> Unit) {
        entitySubscribers.remove(action)
    }

    fun registerChange(transaction: Transaction, change: EntityChange) {
        if (transaction.entityEvents.lastOrNull() != change) {
            transaction.entityEvents.add(change)
        }
    }

    fun alertSubscribers(transaction: Transaction) {
        transaction.entityEvents.forEach { e ->
            entitySubscribers.forEach {
                it(e)
            }
        }
        transaction.entityEvents.clear()
    }

    fun registeredChanges(transaction: Transaction) = transaction.entityEvents.toList()
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