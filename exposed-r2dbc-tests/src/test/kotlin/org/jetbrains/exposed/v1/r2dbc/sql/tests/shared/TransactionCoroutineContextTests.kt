package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertNull

class TransactionCoroutineContextTests : R2dbcDatabaseTestsBase() {
    object UsersTable : Table("users") {
        val idColumn = long("id").autoIncrement()
    }

    @Test
    fun testSuspendMain() {
        Assumptions.assumeTrue(dialect == TestDB.POSTGRESQL)

        val latch = CountDownLatch(1)
        var exception: Throwable? = null

        suspend {
            val db = TestDB.POSTGRESQL.connect()

            suspendTransaction(db) {
                SchemaUtils.create(UsersTable)
            }
        }
            /**
             * The main reason for such a coroutine starting is to get
             * [EmptyCoroutineContext] as coroutine context. It will guarantee
             * that coroutine dispatcher will be `null` what could lead
             * to wrong transaction switching
             */
            .startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    result.onFailure { exception = it }
                    latch.countDown()
                }
            })

        // Wait for completion with timeout
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw AssertionError("Test did not complete within 10 seconds")
        }

        assertNull(exception)
    }
}
