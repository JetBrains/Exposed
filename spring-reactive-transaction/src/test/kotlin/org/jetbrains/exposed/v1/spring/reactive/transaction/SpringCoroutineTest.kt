package org.jetbrains.exposed.v1.spring.reactive.transaction

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.RepeatableTest
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.Rule
import org.junit.Test
import org.springframework.test.annotation.Commit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

open class SpringCoroutineTest : SpringReactiveTransactionTestBase() {

    @Rule
    @JvmField
    val timeout = CoroutinesTimeout.seconds(60)

    object Testing : Table("COROUTINE_TESTING") {
        val id = integer("id").autoIncrement()

        override val primaryKey = PrimaryKey(id)
    }

    @OptIn(InternalApi::class)
    @BeforeTest
    fun beforeTest() {
        // TODO - this should not be done, but transaction is not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @OptIn(InternalApi::class)
    @AfterTest
    fun afterTest() {
        // TODO - this should not be done, but transaction is not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @RepeatableTest(times = 5)
    @Test
//    @Transactional // see [runTestWithMockTransactional]
    @Commit
    open fun testNestedCoroutineTransaction() = runTestWithMockTransactional {
        try {
            SchemaUtils.create(Testing)

            val mainJob = GlobalScope.async {
                val results = (1..5).map { indx ->
                    async(Dispatchers.IO) {
                        suspendTransaction {
                            Testing.insert { }
                            indx
                        }
                    }
                }.awaitAll()

                assertEquals(15, results.sum())
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }

            suspendTransaction {
                assertEquals(5L, Testing.selectAll().count())
            }
        } finally {
            SchemaUtils.drop(Testing)
        }
    }
}
