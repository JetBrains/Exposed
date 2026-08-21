package org.jetbrains.exposed.v1.spring7.reactive.transaction

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.transactions.TransactionsHolderProvider
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.springframework.transaction.reactive.TransactionContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Marker interface solely for Spring resources bound into a reactive [TransactionContext], which carry an
 * Exposed [R2dbcTransaction] alongside their Spring-managed [io.r2dbc.spi.Connection]. This grouped resource
 * is handled by private `SpringReactiveTransactionManager.ExposedHolderObject`.
 */
internal interface ExposedTransactionResource {
    val transaction: R2dbcTransaction
}

/**
 * Defines an element in a coroutine context that installs the active Exposed transactions held by
 * Spring's reactive [TransactionContext] onto [SpringReactiveTransactionsStack] every time the coroutine with
 * this element in the context is resumed on a thread.
 *
 * Spring's [TransactionContext] is the single source of truth for any transactions lifetime in this case.
 * This element is a per-resume projection only. This means that: on [updateThreadContext] it snapshots whatever is
 * currently on the stack, then replaces it with all the transactions found by walking bound resources from
 * the current Reactor `Context`. Then on [restoreThreadContext], it always restores the previous snapshot.
 */
@OptIn(InternalApi::class)
internal class SpringReactiveTransactionContextElement :
    ThreadContextElement<TransactionStackState>,
    AbstractCoroutineContextElement(SpringReactiveTransactionContextElement) {

    companion object Key : CoroutineContext.Key<SpringReactiveTransactionContextElement>

    override fun updateThreadContext(context: CoroutineContext): TransactionStackState {
        val previous = TransactionStackState(TransactionsHolderProvider.holder.snapshot())

        val transactionContext: TransactionContext? = context[ReactorContext]?.context
            ?.takeIf { it.hasKey(TransactionContext::class.java) }
            ?.get(TransactionContext::class.java)
        if (transactionContext != null) {
            val springManaged: List<Transaction> = collectExposedTransactions(transactionContext)
            // Always merge on top of any transactions already existing, like during mixed-transactional-mode
            // when a parent suspendTransaction is present & not already managed by Spring's TransactionContext
            val merged = LinkedHashMap<String, Transaction>()
            previous.transactions.forEach { merged[it.transactionId] = it }
            springManaged.forEach { merged[it.transactionId] = it }
            TransactionsHolderProvider.holder.restore(merged.values.toList())
        }

        return previous
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: TransactionStackState) {
        TransactionsHolderProvider.holder.restore(oldState.transactions)
    }

    /**
     * Walks the [TransactionContext] chain from outermost parent to [transactionContext] (innermost/current),
     * collecting the [ExposedTransactionResource.transaction] bound in each context's resources, in
     * insertion order.
     *
     * @return A list with the innermost/most-recently-bound transaction at the end.
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
 * exact call site of a suspend `@Transactional` invocation (inside [ExposedReactiveTransactionContextInterceptor],
 * so before Spring's reactive transaction management builds and subscribes its `Mono` call chain).
 */
internal object SpringReactiveTransactionHandoff {
    private val snapshot = ThreadLocal<TransactionStackState?>()

    inline fun <T> withSnapshot(state: TransactionStackState, block: () -> T): T {
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

    fun currentOrNull(): TransactionStackState? = snapshot.get()
}

/**
 * Calls the specified suspending [block] with a [SpringReactiveTransactionContextElement] installed in the coroutine context,
 * suspends until it completes, and returns the result.
 *
 * This wrapper must be used when relying on [org.springframework.transaction.reactive.TransactionalOperator],
 * because it allows `TransactionManager.current()` to resolve the correct transaction (which Spring is not aware of)
 * for that coroutine's execution.
 *
 * ```kotlin
 * @Service
 * open class AppService {
 *     @Autowired
 *     private lateinit var operator: TransactionalOperator
 *
 *     open suspend fun saveCustomer(name: String) {
 *         withExposedReactiveContext {
 *             operator.executeAndAwait {
 *                 CustomerTable.insert {
 *                     it[id] = UUID.randomUUID()
 *                     it[name] = name
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * Structured child coroutines started from within its lambda inherit the element automatically.
 * Detached coroutines, for example ones started with `GlobalScope`, do not inherit it and must install their own.
 */
suspend fun <T> withExposedReactiveContext(block: suspend () -> T): T {
    if (currentCoroutineContext()[SpringReactiveTransactionContextElement] != null) {
        return block()
    }

    return withContext(SpringReactiveTransactionContextElement()) { block() }
}
