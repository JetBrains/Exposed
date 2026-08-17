package org.jetbrains.exposed.v1.spring7.reactive.transaction

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.transactions.TransactionsThreadLocalState
import org.jetbrains.exposed.v1.core.transactions.captureTransactionsThreadLocalState
import org.jetbrains.exposed.v1.core.transactions.restoreTransactionsThreadLocalState
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.springframework.transaction.reactive.TransactionContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Marker implemented by Spring resources bound into a reactive [TransactionContext] that carry an
 * Exposed [R2dbcTransaction] alongside their Spring-managed connection.
 * @suppress
 */
internal interface ExposedTransactionResource {
    val transaction: R2dbcTransaction
}

/**
 * A coroutine context element that projects the active Exposed transaction(s) held by Spring's reactive
 * [TransactionContext] (found via the [ReactorContext] element) onto [org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack]
 * for the duration of each coroutine execution segment on a carrier thread.
 *
 * Spring's [TransactionContext] (propagated through the Reactor `Context`) is the source of truth for
 * transaction lifetime. This element is a per-resume/per-thread projection only: on
 * [updateThreadContext] it snapshots whatever is currently on the thread-local stack, then replaces it
 * with the chain of Exposed transactions found by walking bound resources (and parent contexts, for
 * nested/suspended Spring transactions) from the current Reactor `Context`. On [restoreThreadContext] it
 * restores the exact previous snapshot, regardless of success, error, or cancellation.
 *
 * This makes `TransactionManager.current()` / `currentTransactionOrNull()` resolve correctly for coroutine
 * code running underneath a Spring-managed reactive transaction, while allowing two independent
 * `TransactionalOperator` subscriptions running on shared dispatchers to retain their own transaction
 * identity, since each carries its own `TransactionContext` through the Reactor `Context` rather than a
 * single JVM-global stack.
 * @suppress
 */
@InternalApi
class SpringReactiveTransactionContextElement :
    ThreadContextElement<TransactionsThreadLocalState>,
    AbstractCoroutineContextElement(SpringReactiveTransactionContextElement) {

    companion object Key : CoroutineContext.Key<SpringReactiveTransactionContextElement>

    override fun updateThreadContext(context: CoroutineContext): TransactionsThreadLocalState {
        val previous = captureTransactionsThreadLocalState()

        val reactorContext = context[ReactorContext]?.context
        val transactionContext = reactorContext
            ?.takeIf { it.hasKey(TransactionContext::class.java) }
            ?.get<TransactionContext>(TransactionContext::class.java)

        if (transactionContext != null) {
            val springManaged = collectExposedTransactions(transactionContext)

            // Merge on top of whatever is already ambient on this thread (for example, a native
            // suspendTransaction that is not itself tracked by Spring's TransactionContext) rather than
            // fully replacing it: mixed-mode code relies on the native outer transaction remaining visible
            // to SpringReactiveTransactionManager.doBegin's own outer-transaction lookup even when Spring
            // has not (yet) bound any resource of its own.
            val merged = LinkedHashMap<String, Transaction>()
            previous.transactions.forEach { merged[it.transactionId] = it }
            springManaged.forEach { merged[it.transactionId] = it }

            restoreTransactionsThreadLocalState(TransactionsThreadLocalState(merged.values.toList()))
        }

        return previous
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: TransactionsThreadLocalState) {
        restoreTransactionsThreadLocalState(oldState)
    }

    /**
     * Walks the [TransactionContext] chain from outermost parent to [transactionContext] (innermost/current),
     * collecting the [ExposedTransactionResource.transaction] bound in each context's resources, in
     * insertion order, so that the innermost/most-recently-bound transaction ends up on top of the
     * projected stack.
     */
    private fun collectExposedTransactions(transactionContext: TransactionContext): List<Transaction> {
        val chain = generateSequence(transactionContext) { it.parent }.toList().asReversed()

        val transactions = LinkedHashMap<String, Transaction>()
        chain.forEach { ctx ->
            ctx.resources.values
                .filterIsInstance<ExposedTransactionResource>()
                .forEach { transactions[it.transaction.transactionId] = it.transaction }
        }

        return transactions.values.toList()
    }
}

/**
 * Thread-local handoff carrying an immutable snapshot of the Exposed transaction stack captured at the
 * exact call site of a `@Transactional suspend` invocation (inside
 * [ExposedReactiveTransactionContextInterceptor], before Spring's reactive transaction machinery builds
 * and subscribes its `Mono` chain).
 *
 * Spring's [org.springframework.transaction.interceptor.TransactionAspectSupport.ReactiveTransactionSupport]
 * resolves `doGetTransaction`/`isExistingTransaction`/`doBegin` (see [SpringReactiveTransactionManager.doBegin])
 * as part of `createTransactionIfNecessary`, which runs and completes *before* the coroutine that will
 * eventually carry [SpringReactiveTransactionContextElement] is even created (that coroutine is only
 * created once `invocation.proceedWithInvocation()` is invoked inside `Mono.usingWhen`'s `tx -> ...`
 * callback). So `doBegin`'s own mixed-mode outer-transaction lookup cannot rely on the coroutine element;
 * it instead reads this handoff, which is set synchronously on the calling thread immediately before that
 * whole reactive chain is constructed.
 *
 * This relies on Spring's transaction chain subscribing synchronously within `proceed()`, which is the
 * behavior of current Spring 7 versions but an internal detail rather than a published contract. If an
 * async boundary were ever introduced there, the handoff read simply misses on the subscribing thread and
 * outer-transaction resolution degrades to the live thread-local for that database, or to `null` (yielding
 * an independent transaction). Because the handoff and the live read are both thread-locals resolved on the
 * reading thread and both are filtered by database, resolution can never land on another coroutine's
 * transaction.
 *
 * This is a call-scoped handoff only, never a transaction-lifetime store: it is cleared in a `finally`
 * block by the code that set it.
 * @suppress
 */
@InternalApi
internal object SpringReactiveTransactionHandoff {
    private val snapshot = ThreadLocal<TransactionsThreadLocalState?>()

    /**
     * Runs [block] with [state] installed as the current thread's handoff snapshot, restoring the exact
     * previous handoff value (or clearing it, if previously absent) afterward.
     */
    inline fun <T> withSnapshot(state: TransactionsThreadLocalState, block: () -> T): T {
        val previous = snapshot.get()
        snapshot.set(state)
        try {
            return block()
        } finally {
            if (previous == null) {
                snapshot.remove()
            } else {
                snapshot.set(previous)
            }
        }
    }

    /** Returns the currently installed handoff snapshot, or `null` if none is installed. */
    fun currentOrNull(): TransactionsThreadLocalState? = snapshot.get()
}

/**
 * Runs [block] with a [SpringReactiveTransactionContextElement] installed in the coroutine context.
 *
 * This element must be present on the coroutine that ultimately calls into Spring reactive transaction
 * management, whether programmatically (through [org.springframework.transaction.reactive.TransactionalOperator])
 * or through `@Transactional suspend` methods invoked directly on that coroutine. It is what allows
 * `TransactionManager.current()` / `currentTransactionOrNull()` to resolve the correct transaction for
 * that coroutine's execution, and what allows two independent, interleaving coroutines - each driving
 * their own Spring reactive transaction on shared dispatchers - to retain their own transaction identity
 * instead of corrupting a JVM-global stack.
 *
 * Structured child coroutines started from [block] inherit the element automatically. Detached coroutines,
 * for example ones started with `GlobalScope`, do not inherit it and must install their own.
 */
@OptIn(InternalApi::class)
suspend fun <T> withExposedReactiveTransactionContext(block: suspend () -> T): T {
    if (currentCoroutineContext()[SpringReactiveTransactionContextElement] != null) {
        return block()
    }
    return withContext(SpringReactiveTransactionContextElement()) { block() }
}
