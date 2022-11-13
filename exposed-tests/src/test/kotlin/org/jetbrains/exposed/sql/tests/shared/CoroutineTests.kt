package org.jetbrains.exposed.sql.tests.shared

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.RepeatableTest
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Rule
import org.junit.Test
import java.lang.Exception
import java.sql.Connection
import java.util.concurrent.Executors
import kotlin.test.assertNotNull

private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

@ExperimentalCoroutinesApi
@DelicateCoroutinesApi
class CoroutineTests : DatabaseTestsBase() {

    object Testing : IntIdTable("COROUTINE_TESTING")

    @Rule
    @JvmField
    val timeout = CoroutinesTimeout.seconds(60)

    @Test @RepeatableTest(10)
    fun suspendedTx() {
        withTables(Testing) {
            val mainJob = GlobalScope.async(singleThreadDispatcher) {

                val job = launch(singleThreadDispatcher) {
                    newSuspendedTransaction(db = db) {
                        Testing.insert {}

                        suspendedTransaction {
                            assertEquals(1, Testing.select { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value)
                        }
                    }
                }

                job.join()
                val result = newSuspendedTransaction(singleThreadDispatcher, db = db) {
                    Testing.select { Testing.id.eq(1) }.single()[Testing.id].value
                }

                kotlin.test.assertEquals(1, result)
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(1, Testing.select { Testing.id.eq(1) }.single()[Testing.id].value)
        }
    }

    @Test @RepeatableTest(10)
    fun suspendTxAsync() {
        withTables(Testing) {
            val job = GlobalScope.async {
                val launchResult = suspendedTransactionAsync(Dispatchers.IO, db = db) {
                    Testing.insert {}

                    suspendedTransaction {
                        assertEquals(1, Testing.select { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value)
                    }
                }

                launchResult.await()
                val result = suspendedTransactionAsync(Dispatchers.Default, db = db) {
                    Testing.select { Testing.id.eq(1) }.single()[Testing.id].value
                }.await()

                val result2 = suspendedTransactionAsync(Dispatchers.Default, db = db) {
                    assertEquals(1, result)
                    Testing.selectAll().count()
                }

                kotlin.test.assertEquals(1, result2.await())
            }

            while (!job.isCompleted) Thread.sleep(100)

            job.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(1, Testing.selectAll().count())
        }
    }

    @Test @RepeatableTest(10)
    fun nestedSuspendTxTest() {
        suspend fun insertTesting(db: Database) = newSuspendedTransaction(db = db) {
            Testing.insert {}
        }
        withTables(listOf(TestDB.SQLITE), Testing) {
            val mainJob = GlobalScope.async {

                val job = launch(Dispatchers.IO) {
                    newSuspendedTransaction(db = db) {
                        connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                        assertEquals(null, Testing.select { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id))

                        insertTesting(db)

                        assertEquals(1, Testing.select { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value)
                    }
                }

                job.join()
                val result = newSuspendedTransaction(Dispatchers.Default, db = db) {
                    Testing.select { Testing.id.eq(1) }.single()[Testing.id].value
                }

                kotlin.test.assertEquals(1, result)
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(1, Testing.select { Testing.id.eq(1) }.single()[Testing.id].value)
        }
    }

    @Test @RepeatableTest(10)
    fun nestedSuspendAsyncTxTest() {
        withTables(listOf(TestDB.H2, TestDB.H2_MYSQL, TestDB.SQLITE), Testing) {
            val mainJob = GlobalScope.async {
                val job = launch(Dispatchers.IO) {
                    newSuspendedTransaction(db = db) {
                        repeat(10) {
                            Testing.insert { }
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
            assertEquals(10, Testing.selectAll().count())
        }
    }

    @Test @RepeatableTest(10)
    fun awaitAllTest() {
        withTables(listOf(TestDB.SQLITE), Testing) {
            val mainJob = GlobalScope.async {

                val results = (1..5).map { indx ->
                    suspendedTransactionAsync(Dispatchers.IO, db = db) {
                        Testing.insert { }
                        indx
                    }
                }.awaitAll()

                kotlin.test.assertEquals(15, results.sum())
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }

            assertEquals(5L, Testing.selectAll().count())
        }
    }

    @Test @RepeatableTest(10)
    fun suspendedAndNormalTransactions() {
        withTables(Testing) {
            val db = this.db
            var suspendedOk = true
            var normalOk = true
            val mainJob = GlobalScope.launch {
                newSuspendedTransaction(singleThreadDispatcher, db = db) {
                    try {
                        Testing.selectAll().toList()
                    } catch (_: Exception) {
                        suspendedOk = false
                    }
                }

                transaction(db) {
                    try {
                        Testing.selectAll().toList()
                    } catch (_: Exception) {
                        normalOk = false
                    }
                }
            }

            runBlocking {
                mainJob.join()
                kotlin.test.assertTrue(suspendedOk)
                kotlin.test.assertTrue(normalOk)
            }

//            while (!mainJob.isCompleted) Thread.sleep(100)
//            mainJob.getCompletionExceptionOrNull()?.let { throw it }
        }
    }

    class TestingEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<TestingEntity>(Testing)
    }

    @Test fun testCoroutinesWithExceptionWithin() {
        withTables(Testing) {
            val id = Testing.insertAndGetId {}
            commit()

            var connection: ExposedConnection<*>? = null
            val mainJob = GlobalScope.async(singleThreadDispatcher) {
                suspendedTransactionAsync(db = db) {
                    connection = this.connection
                    TestingEntity.new(id.value) {}
                }.await()
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            assertNotNull(connection)
            assertTrue(connection!!.isClosed)
            assertTrue(mainJob.getCompletionExceptionOrNull() is ExposedSQLException)
            assertEquals(1, Testing.selectAll().count())
        }
    }
}
