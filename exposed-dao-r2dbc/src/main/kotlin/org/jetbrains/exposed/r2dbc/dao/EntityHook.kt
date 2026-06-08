package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.transactions.transactionScope
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue

/** Represents the possible states of an [Entity] throughout its lifecycle. */
@ExperimentalR2dbcDaoApi
enum class EntityChangeType {
    /** The entity has been inserted in the database. */
    Created,

    /** The entity has been updated in the database. */
    Updated,

    /** The entity has been removed from the database. */
    Removed
}

/** Stores details about a state-change event for an [Entity] instance. */
@ExperimentalR2dbcDaoApi
data class EntityChange(
    /** The [EntityClass] of the changed entity instance. */
    val entityClass: EntityClass<*, Entity<*>>,
    /** The unique [EntityID] associated with the entity instance. */
    val entityId: EntityID<*>,
    /** The exact changed state of the event. */
    val changeType: EntityChangeType,
    /** The unique id for the [R2dbcTransaction] in which the event took place. */
    val transactionId: String
)

/**
 * Returns the actual [Entity] instance associated with [this][EntityChange] event,
 * or `null` if the entity is not found.
 */
@Suppress("UNCHECKED_CAST")
@ExperimentalR2dbcDaoApi
suspend fun <ID : Any, T : Entity<ID>> EntityChange.toEntity(): T? =
    (entityClass as EntityClass<ID, T>).findById(entityId as EntityID<ID>)

/**
 * Returns the actual [Entity] instance associated with [this][EntityChange] event,
 * or `null` if either its class type is neither equivalent to nor a subclass of [klass],
 * or if the entity is not found.
 */
@ExperimentalR2dbcDaoApi
suspend fun <ID : Any, T : Entity<ID>> EntityChange.toEntity(klass: EntityClass<ID, T>): T? {
    if (!entityClass.isAssignableTo(klass)) return null
    return toEntity<ID, T>()
}

private val R2dbcTransaction.unprocessedEvents: Deque<EntityChange> by transactionScope { ConcurrentLinkedDeque() }
private val R2dbcTransaction.entityEvents: Deque<EntityChange> by transactionScope { ConcurrentLinkedDeque() }
private val entitySubscribers = ConcurrentLinkedQueue<suspend (EntityChange) -> Unit>()

/**
 * Class responsible for providing functions that expose [EntityChange] state logic and entity lifecycle features
 * for alerting triggers or customizing additional functionality. Subscribers are `suspend` because the
 * R2DBC lifecycle runs inside coroutines.
 */
@ExperimentalR2dbcDaoApi
object EntityHook {
    /**
     * Registers a specific state-change [action] for alerts and returns the [action].
     */
    fun subscribe(action: suspend (EntityChange) -> Unit): suspend (EntityChange) -> Unit {
        entitySubscribers.add(action)
        return action
    }

    /** Unregisters a specific state-change [action] from alerts. */
    fun unsubscribe(action: suspend (EntityChange) -> Unit) {
        entitySubscribers.remove(action)
    }
}

/** Creates a new [EntityChange] with [this][R2dbcTransaction] id and registers it as an entity event. */
@ExperimentalR2dbcDaoApi
fun R2dbcTransaction.registerChange(
    entityClass: EntityClass<*, Entity<*>>,
    entityId: EntityID<*>,
    changeType: EntityChangeType
) {
    EntityChange(entityClass, entityId, changeType, transactionId).let {
        if (unprocessedEvents.peekLast() != it) {
            unprocessedEvents.addLast(it)
            entityEvents.addLast(it)
        }
    }
}

private var isProcessingEventsLaunched by transactionScope { false }

/**
 * Triggers alerts for all unprocessed entity events using any state-change actions previously
 * registered via [EntityHook.subscribe].
 */
@ExperimentalR2dbcDaoApi
suspend fun R2dbcTransaction.alertSubscribers() {
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

/** Returns a list of all [EntityChange] events that have been registered in this [R2dbcTransaction]. */
@ExperimentalR2dbcDaoApi
fun R2dbcTransaction.registeredChanges(): List<EntityChange> = entityEvents.toList()

/**
 * Calls the specified [body] with the given state-change [action], registers the action, and
 * returns its result.
 *
 * The [action] will be unregistered at the end of the call to the [body] block.
 */
suspend fun <T> withHook(action: suspend (EntityChange) -> Unit, body: suspend () -> T): T {
    EntityHook.subscribe(action)
    return try {
        body().also {
            TransactionManager.currentOrNull()?.commit()
        }
    } finally {
        EntityHook.unsubscribe(action)
    }
}
