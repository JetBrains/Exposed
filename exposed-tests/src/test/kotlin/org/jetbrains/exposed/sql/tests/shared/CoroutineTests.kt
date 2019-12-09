package org.jetbrains.exposed.sql.tests.shared

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.test.utils.RepeatableTest
import org.junit.Rule
import org.junit.Test
import java.sql.Connection

@ExperimentalCoroutinesApi
class CoroutineTests : DatabaseTestsBase() {

    object Testing : Table("COROUTINE_TESTING") {
        val id = integer("id").primaryKey().autoIncrement() // Column<Int>
    }

    @Rule
    @JvmField
    val timeout = CoroutinesTimeout.seconds(60)

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
                }.await()

                val result2 = suspendedTransactionAsync(Dispatchers.Default, db = db) {
                    assertEquals(1, result)
                    Testing.selectAll().count()
                }

                kotlin.test.assertEquals(1, result2.await())
            }

            while (!job.isCompleted) Thread.sleep(100)

            job.getCompletionExceptionOrNull()?.let { throw it }
        }
    }

    @Test @RepeatableTest(10)
    fun nestedSuspendTxTest() {
        suspend fun insertTesting(db : Database) =  newSuspendedTransaction(db = db) {
            Testing.insert {}
        }
        withTables(listOf(TestDB.SQLITE), Testing) {
            val mainJob = GlobalScope.async {

                val job = launch(Dispatchers.IO) {
                    newSuspendedTransaction(db = db) {
                        connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                        assertEquals(null, Testing.select { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id))

                        insertTesting(db)

                        assertEquals(1, Testing.select { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id))
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
    fun nestedSuspendAsyncTxTest() {
        withTables(listOf(TestDB.H2, TestDB.H2_MYSQL, TestDB.SQLITE), Testing) {
            val mainJob = GlobalScope.async {
                val job = launch(Dispatchers.IO) {
                    newSuspendedTransaction(db = db) {
                        repeat(10) {
                            Testing.insert {  }
                        }
                        commit()
                        (1..10).map {
                            suspendedTransactionAsync {
                                Testing.selectAll().toList()
                            }
                        }.awaitAll()
                    }
                }

                job.join()
                val result = newSuspendedTransaction(Dispatchers.Default, db = db) {
                    Testing.selectAll().count()
                }

                kotlin.test.assertEquals(10, result)
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
        }
    }

    @Test @RepeatableTest(10)
    fun awaitAllTest() {
        withTables(listOf(TestDB.SQLITE), Testing) {
            val mainJob = GlobalScope.async {

                val results = (1..5).map { indx ->
                    suspendedTransactionAsync(Dispatchers.IO, db = db) {
                        Testing.insert {  }
                        indx
                    }
                }.awaitAll()

                kotlin.test.assertEquals(15, results.sum())
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }

            transaction {
                assertEquals(5, Testing.selectAll().count())
            }
        }
    }
}