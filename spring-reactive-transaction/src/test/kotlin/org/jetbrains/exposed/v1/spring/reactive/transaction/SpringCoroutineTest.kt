package org.jetbrains.exposed.v1.spring.reactive.transaction

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.RepeatableTest
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.Rule
import org.junit.Test
import org.springframework.test.annotation.Commit
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

open class SpringCoroutineTest : SpringReactiveTransactionTestBase() {

    @Rule
    @JvmField
    val timeout = CoroutinesTimeout.seconds(60)

    object Testing : Table("COROUTINE_TESTING") {
        val id = integer("id").autoIncrement()

        override val primaryKey = PrimaryKey(id)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @RepeatableTest(times = 5)
    @Test
    @Transactional
    @Commit
    open fun testNestedCoroutineTransaction() = runTest {
        try {
            SchemaUtils.create(Testing)

            val mainJob = GlobalScope.async {
                val results = (1..5).map { indx ->
                    withContext(Dispatchers.IO) {
                        suspendTransaction {
                            Testing.insert { }
                            indx
                        }
                    }
                }

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
