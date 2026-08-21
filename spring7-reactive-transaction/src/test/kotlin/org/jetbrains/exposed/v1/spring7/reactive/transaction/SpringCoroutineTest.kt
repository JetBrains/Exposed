package org.jetbrains.exposed.v1.spring7.reactive.transaction

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.springframework.test.annotation.Commit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.milliseconds

open class SpringCoroutineTest : SpringReactiveTransactionTestBase() {
    object Testing : Table("COROUTINE_TESTING") {
        val id = integer("id").autoIncrement()

        override val primaryKey = PrimaryKey(id)
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    @Commit
    open fun testNestedCoroutineTransaction() = runTestWithMockTransactional {
        try {
            SchemaUtils.create(Testing)

            // Detached coroutines (GlobalScope) do not inherit the Spring-managed transaction context,
            // so they must resolve the database explicitly instead of relying on the ambient default,
            // which may point at another database registered by an unrelated Spring context in this JVM.
            val database = TransactionManager.current().db

            val mainJob = GlobalScope.async {
                // @CoroutinesTimeout is not compatible with @Transactional
                val results = withTimeout(1000.milliseconds) {
                    (1..5).map { indx ->
                        async(Dispatchers.IO) {
                            suspendTransaction(db = database) {
                                Testing.insert { }
                                indx
                            }
                        }
                    }.awaitAll()
                }

                assertEquals(15, results.sum())
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }

            assertEquals(5L, Testing.selectAll().count())
        } finally {
            SchemaUtils.drop(Testing)
        }
    }

    @Test
    fun `concurrent reactive transactions retain their own current transaction`() = runTest {
        val firstTransactionStarted = CompletableDeferred<String>()
        val secondTransactionStarted = CompletableDeferred<Unit>()
        val firstTransactionChecked = CompletableDeferred<Unit>()

        coroutineScope {
            val firstTransaction = async {
                transactionManager.execute {
                    val expectedTransactionId = TransactionManager.current().transactionId
                    firstTransactionStarted.complete(expectedTransactionId)
                    secondTransactionStarted.await()
                    assertEquals(expectedTransactionId, TransactionManager.current().transactionId)
                    firstTransactionChecked.complete(Unit)
                }
            }

            val secondTransaction = async {
                val firstTransactionId = firstTransactionStarted.await()
                transactionManager.execute {
                    val expectedTransactionId = TransactionManager.current().transactionId
                    assertNotEquals(firstTransactionId, expectedTransactionId)
                    secondTransactionStarted.complete(Unit)
                    firstTransactionChecked.await()
                    assertEquals(expectedTransactionId, TransactionManager.current().transactionId)
                }
            }

            awaitAll(firstTransaction, secondTransaction)
        }
    }
}
