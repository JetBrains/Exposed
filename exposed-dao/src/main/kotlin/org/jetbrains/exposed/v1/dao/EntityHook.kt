package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.transactions.currentTransactionOrNull
import org.jetbrains.exposed.v1.core.transactions.transactionScope
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue

/** Represents the possible states of an [Entity] throughout its lifecycle. */
enum class EntityChangeType {
    /** The entity has been inserted in the database. */
    Created,

    /** The entity has been updated in the database. */
    Updated,

    /** The entity has been removed from the database. */
    Removed
}

/** Stores details about a state-change event for an [Entity] instance. */
data class EntityChange(
    /** The [EntityClass] of the changed entity instance. */
    val entityClass: EntityClass<*, Entity<*>>,
    /** The unique [EntityID] associated with the entity instance. */
    val entityId: EntityID<*>,
    /** The exact changed state of the event. */
    val changeType: EntityChangeType,
    /** The unique id for the [Transaction] in which the event took place. */
    val transactionId: String
)

/**
 * Returns the actual [Entity] instance associated with [this][EntityChange] event,
 * or `null` if the entity is not found.
 */
fun <ID : Any, T : Entity<ID>> EntityChange.toEntity(): T? =
    (entityClass as EntityClass<ID, T>).findById(entityId as EntityID<ID>)

/**
 * Returns the actual [Entity] instance associated with [this][EntityChange] event,
 * or `null` if either its [EntityClass] type is neither equivalent to nor a subclass of [klass],
 * or if the entity is not found.
 */
fun <ID : Any, T : Entity<ID>> EntityChange.toEntity(klass: EntityClass<ID, T>): T? {
    if (!entityClass.isAssignableTo(klass)) return null
    return toEntity<ID, T>()
}

private val Transaction.unprocessedEvents: Deque<EntityChange> by transactionScope { ConcurrentLinkedDeque() }
private val Transaction.entityEvents: Deque<EntityChange> by transactionScope { ConcurrentLinkedDeque() }
private val entitySubscribers = ConcurrentLinkedQueue<(EntityChange) -> Unit>()

/**
 * Class responsible for providing functions that expose [EntityChange] state logic and entity lifecycle features
 * for alerting triggers or customizing additional functionality.
 */
object EntityHook {
    /**
     * Registers a specific state-change [action] for alerts and returns the [action].
     *
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityHookTest.testCallingFlushNotifiesEntityHookSubscribers
     */
    fun subscribe(action: (EntityChange) -> Unit): (EntityChange) -> Unit {
        entitySubscribers.add(action)
        return action
    }

    /** Unregisters a specific state-change [action] from alerts. */
    fun unsubscribe(action: (EntityChange) -> Unit) {
        entitySubscribers.remove(action)
    }
}

/** Creates a new [EntityChange] with [this][Transaction] id and registers it as an entity event. */
fun Transaction.registerChange(
    entityClass: EntityClass<*, Entity<*>>,
    entityId: EntityID<*>,
    changeType: EntityChangeType
) {
    EntityChange(entityClass, entityId, changeType, id).let {
        if (unprocessedEvents.peekLast() != it) {
            unprocessedEvents.addLast(it)
            entityEvents.addLast(it)
        }
    }
}

private var isProcessingEventsLaunched by transactionScope { false }

/**
 * Triggers alerts for all unprocessed entity events using any state-change actions previously registered
 * via [EntityHook.subscribe].
 */
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

/** Returns a list of all [EntityChange] events that have been registered in this [Transaction]. */
fun Transaction.registeredChanges() = entityEvents.toList()

/**
 * Calls the specified function [body] with the given state-change [action], registers the action, and returns its result.
 *
 * The [action] will be unregistered at the end of the call to the [body] block.
 */
fun <T> withHook(action: (EntityChange) -> Unit, body: () -> T): T {
    EntityHook.subscribe(action)
    try {
        return body().apply {
            @OptIn(InternalApi::class)
            (currentTransactionOrNull() as? JdbcTransaction?)?.commit()
        }
    } finally {
        EntityHook.unsubscribe(action)
    }
}
