package org.jetbrains.exposed.v1.tests.shared

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit5.CoroutinesTimeout
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.withSuspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.MISSING_R2DBC_TEST
import org.jetbrains.exposed.v1.tests.NOT_APPLICABLE_TO_R2DBC
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
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

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
    fun suspendedTx() {
        withTables(Testing) {
            val mainJob = GlobalScope.async(singleThreadDispatcher) {
                val job = launch(singleThreadDispatcher) {
                    newSuspendedTransaction(db = db) {
                        Testing.insert {}

                        withSuspendTransaction {
                            assertEquals(1, Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value)
                        }
                    }
                }

                job.join()
                val result = newSuspendedTransaction(singleThreadDispatcher, db = db) {
                    Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value
                }

                kotlin.test.assertEquals(1, result)
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(1, Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value)
        }
    }

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
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
                        maxAttempts = 20

                        // throws JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation
                        // until original row is updated with a new id
                        TestingUnique.insert { it[id] = originalId }

                        assertEquals(2, TestingUnique.selectAll().count())
                    }
                }
                val updateJob = launch {
                    newSuspendedTransaction(Dispatchers.Default, db = db) {
                        maxAttempts = 20

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

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
    fun suspendTxAsync() {
        withTables(Testing) {
            val job = GlobalScope.async {
                val launchResult = suspendedTransactionAsync(Dispatchers.IO, db = db) {
                    Testing.insert {}

                    withSuspendTransaction {
                        assertEquals(
                            1,
                            Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value
                        )
                    }
                }

                launchResult.await()
                val result = suspendedTransactionAsync(Dispatchers.Default, db = db) {
                    Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value
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

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
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
                        maxAttempts = 20

                        // throws JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation
                        // until original row is updated with a new id
                        TestingUnique.insert { it[id] = originalId }

                        TestingUnique.selectAll().count()
                    },
                    suspendedTransactionAsync(db = db) {
                        maxAttempts = 20

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

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
    fun nestedSuspendTxTest() {
        suspend fun insertTesting(db: Database) = newSuspendedTransaction(db = db) {
            Testing.insert {}
        }
        withTables(listOf(TestDB.SQLITE), Testing) {
            val mainJob = GlobalScope.async {
                val job = launch(Dispatchers.IO) {
                    newSuspendedTransaction(db = db) {
                        connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                        assertEquals(
                            null,
                            Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)
                        )

                        insertTesting(db)

                        assertEquals(
                            1,
                            Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value
                        )
                    }
                }

                job.join()
                val result = newSuspendedTransaction(Dispatchers.Default, db = db) {
                    Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value
                }

                kotlin.test.assertEquals(1, result)
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(1, Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value)
        }
    }

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
    fun nestedSuspendAsyncTxTest() {
        withTables(listOf(TestDB.H2_V2, TestDB.H2_V2_MYSQL, TestDB.SQLITE), Testing) {
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

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
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

    @Tag(NOT_APPLICABLE_TO_R2DBC)
    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
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

    @Tag(MISSING_R2DBC_TEST)
    @Test
    @CoroutinesTimeout(60000)
    fun testCoroutinesWithExceptionWithin() {
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
            assertTrue(connection.isClosed)
            assertTrue(mainJob.getCompletionExceptionOrNull() is ExposedSQLException)
            assertEquals(1, Testing.selectAll().count())
        }
    }
}
