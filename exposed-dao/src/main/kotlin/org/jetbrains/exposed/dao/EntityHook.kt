package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transactionScope
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue

enum class EntityChangeType {
    Created,
    Updated,
    Removed;
}

data class EntityChange(
    val entityClass: EntityClass<*, Entity<*>>,
    val entityId: EntityID<*>,
    val changeType: EntityChangeType,
    val transactionId: String
)

fun <ID : Comparable<ID>, T : Entity<ID>> EntityChange.toEntity(): T? = (entityClass as EntityClass<ID, T>).findById(entityId as EntityID<ID>)

fun <ID : Comparable<ID>, T : Entity<ID>> EntityChange.toEntity(klass: EntityClass<ID, T>): T? {
    if (!entityClass.isAssignableTo(klass)) return null
    @Suppress("UNCHECKED_CAST")
    return toEntity<ID, T>()
}

private val Transaction.unprocessedEvents: Deque<EntityChange> by transactionScope { ConcurrentLinkedDeque() }
private val Transaction.entityEvents: Deque<EntityChange> by transactionScope { ConcurrentLinkedDeque() }
private val entitySubscribers = ConcurrentLinkedQueue<(EntityChange) -> Unit>()

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
        if (unprocessedEvents.peekLast() != it) {
            unprocessedEvents.addLast(it)
            entityEvents.addLast(it)
        }
    }
}


private var isProcessingEventsLaunched by transactionScope { false }
fun Transaction.alertSubscribers() {
    if (isProcessingEventsLaunched) return
    while (true) {
        try {
            isProcessingEventsLaunched = true
            val event = unprocessedEvents.pollFirst() ?: break
            entitySubscribers.forEach { it(event) }
        } finally {
            isProcessingEventsLaunched = false
        }
    }
}

fun Transaction.registeredChanges() = entityEvents.toList()

fun <T> withHook(action: (EntityChange) -> Unit, body: () -> T): T {
    EntityHook.subscribe(action)
    try {
        return body().apply {
            TransactionManager.current().commit()
        }
    } finally {
        EntityHook.unsubscribe(action)
    }
}
