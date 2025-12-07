package org.jetbrains.exposed.v1.spring.reactive.transaction

import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.RepeatedTest
import org.springframework.test.annotation.Commit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

open class SpringCoroutineTest : SpringReactiveTransactionTestBase() {
    object Testing : Table("COROUTINE_TESTING") {
        val id = integer("id").autoIncrement()

        override val primaryKey = PrimaryKey(id)
    }

    @OptIn(InternalApi::class)
    @BeforeTest
    fun beforeTest() {
        // TODO - this should not be done, but transaction is not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()
            ?.joinToString(separator = "\n", prefix = "\n!!! ORPHAN transactions:\n") { "--> $it" }
            ?.ifEmpty { "NO transactions to clear up :)" }
            ?.also { println(it) }
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
        println("\n-----------STARTING TEST-----------")
    }

    @OptIn(InternalApi::class)
    @AfterTest
    fun afterTest() {
        println("\n-----------FINISHED TEST-----------")
        // TODO - this should not be done, but transaction is not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()
            ?.joinToString(separator = "\n", prefix = "\n!!! ORPHAN transactions:\n") { "--> $it" }
            ?.ifEmpty { "NO transactions to clear up :)" }
            ?.also { println(it) }
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    @Commit
    open fun testNestedCoroutineTransaction() = runTestWithMockTransactional {
        try {
            SchemaUtils.create(Testing)

            val mainJob = GlobalScope.async {
                // @CoroutinesTimeout is not compatible with @Transactional
                val results = withTimeout(1000) {
                    (1..5).map { indx ->
                        async(Dispatchers.IO) {
                            suspendTransaction {
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
}
