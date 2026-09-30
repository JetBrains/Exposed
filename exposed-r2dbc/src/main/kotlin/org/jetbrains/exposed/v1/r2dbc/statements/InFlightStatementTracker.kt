package org.jetbrains.exposed.v1.r2dbc.statements

import io.r2dbc.spi.Connection
import io.r2dbc.spi.Wrapped
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks whether a statement may still be executing on the database for a single connection.
 *
 * R2DBC drivers typically drain a statement's results when its subscription is cancelled, rather than aborting it,
 * so a cancelled subscription does not mean the statement has stopped running.
 * A statement is therefore considered in flight from the moment its result publisher is subscribed until that
 * publisher emits a terminal signal, which never arrives if the subscription is cancelled by a downstream consumer.
 *
 * Only the most recently subscribed execution is tracked, as statements on a single connection are executed
 * sequentially, so the latest execution terminating means every earlier one has terminated as well.
 */
internal class InFlightStatementTracker {
    private val latest = AtomicReference<Any?>(null)

    /** Whether a statement may currently be executing on the database. */
    val isStatementInFlight: Boolean
        get() = latest.get() != null

    /** Forgets about any in-flight statement, for example after it has been cancelled. */
    fun reset() {
        latest.set(null)
    }

    /** Returns a [Publisher] that marks a statement as in flight while [publisher] is subscribed and not terminated. */
    fun <T> track(publisher: Publisher<T>): Publisher<T> = Publisher { subscriber ->
        val token = Any()
        publisher.subscribe(object : Subscriber<T> {
            override fun onSubscribe(subscription: Subscription) {
                latest.set(token)
                subscriber.onSubscribe(subscription)
            }

            override fun onNext(item: T) {
                subscriber.onNext(item)
            }

            override fun onError(cause: Throwable) {
                latest.compareAndSet(token, null)
                subscriber.onError(cause)
            }

            override fun onComplete() {
                latest.compareAndSet(token, null)
                subscriber.onComplete()
            }
        })
    }
}

/**
 * Sends a driver-specific request to abort whichever statement is currently executing on [connection].
 *
 * Wrapping connections (for example, from `r2dbc-pool`) are unwrapped using [Wrapped] until a supported driver
 * connection is found. If the driver does not support cancelling a running statement, this is a no-op.
 *
 * @return `true` if a cancel request was sent, `false` if the driver does not support cancelling statements.
 */
internal suspend fun cancelRunningStatement(connection: Connection): Boolean {
    val driverConnection = unwrapDriverConnection(connection)
    val cancelRequest = postgresCanceller?.cancelRequest(driverConnection) ?: return false
    cancelRequest.awaitFirstOrNull()
    return true
}

private const val MAX_UNWRAP_DEPTH = 16

private fun unwrapDriverConnection(connection: Connection): Any {
    var current: Any = connection
    repeat(MAX_UNWRAP_DEPTH) {
        if (postgresCanceller?.supports(current) == true) return current
        val unwrapped = (current as? Wrapped<*>)?.unwrap()
        if (unwrapped == null || unwrapped === current) return current
        current = unwrapped
    }
    return current
}

/** Only resolved if the PostgreSQL R2DBC driver is present on the classpath, as it is a `compileOnly` dependency. */
private val postgresCanceller: PostgresStatementCanceller? by lazy {
    try {
        Class.forName("io.r2dbc.postgresql.api.PostgresqlConnection")
        PostgresStatementCanceller
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: LinkageError) {
        null
    }
}

/**
 * Uses the PostgreSQL protocol `CancelRequest` message, which is sent over a separate connection and aborts
 * whatever the target backend is executing. A cancel received while the backend is idle is ignored by the server.
 */
private object PostgresStatementCanceller {
    fun supports(connection: Any): Boolean = connection is io.r2dbc.postgresql.api.PostgresqlConnection

    fun cancelRequest(connection: Any): Publisher<Void>? =
        (connection as? io.r2dbc.postgresql.api.PostgresqlConnection)?.cancelRequest()
}
