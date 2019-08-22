package org.jetbrains.exposed.sql.tests.shared

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.transactions.experimental.andThen
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.test.utils.RepeatableTest
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class CoroutineTests : DatabaseTestsBase() {

    object Testing : Table("COROUTINE_TESTING") {
        val id = integer("id").autoIncrement().primaryKey() // Column<Int>
    }

    @Rule
    @JvmField
    val timeout = CoroutinesTimeout.seconds(100)

    @Test @RepeatableTest(10)
    fun suspendedTx() {
        withTables(Testing) {
            val mainJob = GlobalScope.async {

                val job = launch(Dispatchers.IO) {
                    newSuspendedTransaction(db = db) {
                        Testing.insert {}

                        suspendedTransaction {
                            assertEquals(1, Testing.select { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id))
                        }
                    }
                }

                job.join()
                val result = newSuspendedTransaction(Dispatchers.Default, db = db) {
                    Testing.select { Testing.id.eq(1) }.single()[Testing.id]
                }

                kotlin.test.assertEquals(1, result)
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
        }
    }

    @Test @RepeatableTest(10)
    fun suspendTxAsync() {
        withTables(Testing) {
            val job = GlobalScope.async {
                val launchResult = suspendedTransactionAsync(Dispatchers.IO, db = db) {
                    Testing.insert{}

                    suspendedTransaction {
                        assertEquals(1, Testing.select { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id))
                    }
                }

                launchResult.await()
                val result = suspendedTransactionAsync(Dispatchers.Default, db = db) {
                    Testing.select { Testing.id.eq(1) }.single()[Testing.id]
                }.andThen {
                    assertEquals(1, it)
                    Testing.selectAll().count()
                }

                kotlin.test.assertEquals(1, result.await())
            }

            while (!job.isCompleted) Thread.sleep(100)

            job.getCompletionExceptionOrNull()?.let { throw it }
        }
    }
}