package org.jetbrains.exposed.v1.spring7.reactive.transaction

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.transactions.TransactionsHolderProvider
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(InternalApi::class)
class GlobalSpringStackTest : SpringReactiveTransactionTestBase() {

    @BeforeEach
    fun reportStack() {
        val holder = TransactionsHolderProvider.holder
        val leaked = holder.snapshot()
        println(
            "holder=${holder::class.simpleName} priority=${holder.priority} " +
                "leftOverStack=${leaked.size} ${leaked.map { it.transactionId }}"
        )
    }

    /** Two transactions that overlap in time, each on its own thread. */
    private fun concurrentBurst() {
        val firstStarted = CompletableDeferred<String>()
        val secondStarted = CompletableDeferred<Unit>()
        val firstChecked = CompletableDeferred<Unit>()
        val failure = AtomicReference<Throwable>()

        val first = thread(name = "repro-first") {
            runCatching {
                runBlocking {
                    transactionManager.execute {
                        val mine = TransactionManager.current().transactionId
                        firstStarted.complete(mine)
                        secondStarted.await()
                        assertEquals(mine, TransactionManager.current().transactionId, "first transaction drifted")
                        firstChecked.complete(Unit)
                    }
                }
            }.onFailure {
                failure.compareAndSet(null, it)
                firstStarted.complete("<failed>")
                firstChecked.complete(Unit)
            }
        }

        val second = thread(name = "repro-second") {
            runCatching {
                runBlocking {
                    val firstId = firstStarted.await()
                    transactionManager.execute {
                        val mine = TransactionManager.current().transactionId
                        assertNotEquals(firstId, mine, "second transaction resolved to the first one")
                        secondStarted.complete(Unit)
                        firstChecked.await()
                        assertEquals(mine, TransactionManager.current().transactionId, "second transaction drifted")
                    }
                }
            }.onFailure {
                failure.compareAndSet(null, it)
                secondStarted.complete(Unit)
            }
        }

        first.join(60_000)
        second.join(60_000)
        assertTrue(!first.isAlive && !second.isAlive, "repro threads did not finish")
        failure.get()?.let { throw it }
    }

    @Test
    fun `one concurrent burst permanently corrupts the global stack`() {
        assertTrue(
            TransactionsHolderProvider.holder.isEmpty(),
            "precondition: global stack must start empty"
        )

        runCatching { concurrentBurst() } // may or may not fail; the damage is what matters

        val leaked = TransactionsHolderProvider.holder.snapshot()
        println("[repro] after burst, global stack holds ${leaked.size}: ${leaked.map { it.transactionId }}")

        // Now do something completely sequential — no concurrency at all.
        val observed = AtomicReference<String>()
        val expected = AtomicReference<String>()
        runBlocking {
            transactionManager.execute {
                expected.set(it.let { _ -> TransactionManager.currentOrNull()?.transactionId }.orEmpty())
                observed.set(TransactionManager.current().transactionId)
            }
        }
        println("[repro] sequential transaction afterwards: current()=${observed.get()}")

        assertTrue(
            leaked.isEmpty(),
            "global stack leaked ${leaked.size} completed transaction(s): ${leaked.map { it.transactionId }}"
        )
    }

    @RepeatedTest(200)
    fun `concurrent transactions on real threads retain their own current transaction`() {
        concurrentBurst()
    }
}
