package org.jetbrains.exposed.v1.spring.transaction

import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.suspendedTransactionAsync
import org.junit.jupiter.api.RepeatedTest
import org.springframework.test.annotation.Commit
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

open class SpringCoroutineTest : SpringTransactionTestBase() {
    object Testing : Table("COROUTINE_TESTING") {
        val id = integer("id").autoIncrement() // Column<Int>

        override val primaryKey = PrimaryKey(id)
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    @RepeatedTest(5)
    @Transactional
    @Commit
    // Is this test flaky?
    open fun testNestedCoroutineTransaction() {
        try {
            SchemaUtils.create(Testing)

            val mainJob = GlobalScope.async {
                // @CoroutinesTimeout is not compatible with @Transactional
                val results = withTimeout(1000) {
                    (1..5).map { indx ->
                        suspendedTransactionAsync(Dispatchers.IO) {
                            Testing.insert { }
                            indx
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
        /*withTables(Testing) {
            val mainJob = GlobalScope.async {

                val job = launch(Dispatchers.IO) {
                    newSuspendedTransaction(db = db) {
                        Testing.insert {}

                        suspendedTransaction {
                            assertEquals(1, Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id))
                        }
                    }
                }

                job.join()
                val result = newSuspendedTransaction(Dispatchers.Default, db = db) {
                    Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id]
                }

                kotlin.test.assertEquals(1, result)
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
        }*/
    }
}
