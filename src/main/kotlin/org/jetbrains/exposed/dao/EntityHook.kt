package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

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


data class EntityChange<ID: Any>(val entityClass: EntityClass<ID, Entity<ID>>, val id: EntityID<ID>, var changeType: EntityChangeType) {
}

fun<ID: Any> EntityChange<ID>.toEntity() : Entity<ID>? {
    return entityClass.findById(id)
}

fun<ID: Any,T: Entity<ID>> EntityChange<*>.toEntity(klass: EntityClass<ID, T>) : T? {
    if (!entityClass.isAssignableTo(klass)) return null
    @Suppress("UNCHECKED_CAST")
    return toEntity() as? T
}

object EntityHook {
    private val entitySubscribers: ArrayList<(EntityChange<*>) -> Unit> = ArrayList()

    fun subscribe (action: (EntityChange<*>) -> Unit): (EntityChange<*>) -> Unit {
        entitySubscribers.add(action)
        return action
    }

    fun unsubscribe (action: (EntityChange<*>) -> Unit) {
        entitySubscribers.remove(action)
    }

    fun alertSubscribers(change: EntityChange<*>) {
        entitySubscribers.forEach { it(change) }
    }
}

fun <T> withHook(action: (EntityChange<*>) -> Unit, body: ()->T): T {
    EntityHook.subscribe(action)
    try {
        return body()
    }
    finally {
        EntityHook.unsubscribe(action)
    }
}

fun<T> transactionWithEntityHook(statement: Transaction.() -> T): Pair<T, Collection<EntityChange<*>>> {
    val changedEntities = mutableMapOf<EntityID<*>, EntityChange<*>>()
    val transactionResult = withHook({ change ->
        val existingChange = changedEntities[change.id]
        val newChangeType = existingChange?.changeType?.merge(change.changeType) ?: change.changeType
        changedEntities[change.id] = (existingChange ?: change).apply {
            changeType = newChangeType
    }
    })
    {
        transaction {
            val result = statement()
            flushCache()
            result
        }
    }
    return transactionResult to changedEntities.values
}
