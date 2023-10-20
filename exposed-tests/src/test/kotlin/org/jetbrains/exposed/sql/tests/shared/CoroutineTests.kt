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
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.experimental.withSuspendTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Rule
import org.junit.Test
import java.sql.Connection
import java.util.concurrent.Executors
import kotlin.test.assertNotNull

private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

@ExperimentalCoroutinesApi
@DelicateCoroutinesApi
class CoroutineTests : DatabaseTestsBase() {

    object Testing : IntIdTable("COROUTINE_TESTING")

    object TestingUnique : Table("COROUTINE_UNIQUE") {
        val id = integer("id").uniqueIndex()
    }

    @Rule
    @JvmField
    val timeout = CoroutinesTimeout.seconds(60)

    @Test
    @RepeatableTest(10)
    fun suspendedTx() {
        withTables(Testing) {
            val mainJob = GlobalScope.async(singleThreadDispatcher) {
                val job = launch(singleThreadDispatcher) {
                    newSuspendedTransaction(db = db) {
                        Testing.insert {}

                        withSuspendTransaction {
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

    @Test
    @RepeatableTest(10)
    fun testSuspendTransactionWithRepetition() {
        withTables(TestingUnique) {
            val (originalId, updatedId) = 1 to 99
            val mainJob = GlobalScope.async(Dispatchers.Default) {
                newSuspendedTransaction(Dispatchers.Default, db = db) {
                    TestingUnique.insert { it[id] = originalId }

                    assertEquals(originalId, TestingUnique.selectAll().single()[TestingUnique.id])
                }

                val insertJob = launch {
                    newSuspendedTransaction(Dispatchers.Default, db = db) {
                        repetitionAttempts = 20

                        // throws JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation
                        // until original row is updated with a new id
                        TestingUnique.insert { it[id] = originalId }

                        assertEquals(2, TestingUnique.selectAll().count())
                    }
                }
                val updateJob = launch {
                    newSuspendedTransaction(Dispatchers.Default, db = db) {
                        repetitionAttempts = 20

                        TestingUnique.update({ TestingUnique.id eq originalId }) { it[id] = updatedId }

                        assertEquals(updatedId, TestingUnique.selectAll().single()[TestingUnique.id])
                    }
                }
                insertJob.join()
                updateJob.join()

                val result = newSuspendedTransaction(Dispatchers.Default, db = db) { TestingUnique.selectAll().count() }
                kotlin.test.assertEquals(2, result, "Failing at end of mainJob")
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEqualCollections(listOf(updatedId, originalId), TestingUnique.selectAll().map { it[TestingUnique.id] })
        }
    }

    @Test
    @RepeatableTest(10)
    fun suspendTxAsync() {
        withTables(Testing) {
            val job = GlobalScope.async {
                val launchResult = suspendedTransactionAsync(Dispatchers.IO, db = db) {
                    Testing.insert {}

                    withSuspendTransaction {
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

    @Test
    @RepeatableTest(10)
    fun testSuspendTransactionAsyncWithRepetition() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), TestingUnique) {
            val (originalId, updatedId) = 1 to 99
            val mainJob = GlobalScope.async(Dispatchers.Default) {
                newSuspendedTransaction(Dispatchers.Default, db = db) {
                    TestingUnique.insert { it[id] = originalId }

                    assertEquals(originalId, TestingUnique.selectAll().single()[TestingUnique.id])
                }

                val (insertResult, updateResult) = listOf(
                    suspendedTransactionAsync(db = db) {
                        repetitionAttempts = 20

                        // throws JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation
                        // until original row is updated with a new id
                        TestingUnique.insert { it[id] = originalId }

                        TestingUnique.selectAll().count()
                    },
                    suspendedTransactionAsync(db = db) {
                        repetitionAttempts = 20

                        TestingUnique.update({ TestingUnique.id eq originalId }) { it[id] = updatedId }
                        TestingUnique.selectAll().count()
                    }
                ).awaitAll()

                kotlin.test.assertEquals(1, updateResult, "Failing at end of update job")
                kotlin.test.assertEquals(2, insertResult, "Failing at end of insert job")
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEqualCollections(listOf(updatedId, originalId), TestingUnique.selectAll().map { it[TestingUnique.id] })
        }
    }

    @Test
    @RepeatableTest(10)
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

    @Test
    @RepeatableTest(10)
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

    @Test
    @RepeatableTest(10)
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

    @Test
    @RepeatableTest(10)
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
