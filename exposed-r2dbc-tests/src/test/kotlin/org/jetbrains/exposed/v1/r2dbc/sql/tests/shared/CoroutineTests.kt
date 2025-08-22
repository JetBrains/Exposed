package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.RepeatableTest
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransactionAsync
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executors

private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

// NOTE: 1 DAO TEST REMOVED + 1 NO RELEVANCE (suspend + 'normal' txs)
@ExperimentalCoroutinesApi
@DelicateCoroutinesApi
class CoroutineTests : R2dbcDatabaseTestsBase() {
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
                    suspendTransaction(db = db) {
                        Testing.insert {}

                        suspendTransaction {
                            assertEquals(1, Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value)
                        }
                    }
                }

                job.join()
                val result = suspendTransaction(singleThreadDispatcher, db = db) {
                    Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value
                }

                kotlin.test.assertEquals(1, result)
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(1, Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value)
        }
    }

    @Test
    @RepeatableTest(10)
    fun testSuspendTransactionWithRepetition() {
        withTables(TestingUnique) {
            val (originalId, updatedId) = 1 to 99
            val mainJob = GlobalScope.async(Dispatchers.Default) {
                suspendTransaction(Dispatchers.Default, db = db) {
                    TestingUnique.insert { it[TestingUnique.id] = originalId }

                    assertEquals(originalId, TestingUnique.selectAll().single()[TestingUnique.id])
                }

                val insertJob = launch {
                    suspendTransaction(Dispatchers.Default, db = db) {
                        maxAttempts = 20

                        // throws JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation
                        // until original row is updated with a new id
                        TestingUnique.insert { it[TestingUnique.id] = originalId }

                        assertEquals(2, TestingUnique.selectAll().count())
                    }
                }
                val updateJob = launch {
                    suspendTransaction(Dispatchers.Default, db = db) {
                        maxAttempts = 20

                        TestingUnique.update({ TestingUnique.id eq originalId }) { it[TestingUnique.id] = updatedId }

                        assertEquals(updatedId, TestingUnique.selectAll().single()[TestingUnique.id])
                    }
                }
                insertJob.join()
                updateJob.join()

                val result = suspendTransaction(Dispatchers.Default, db = db) { TestingUnique.selectAll().count() }
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
                val launchResult = suspendTransactionAsync(Dispatchers.IO, db = db) {
                    Testing.insert {}

                    suspendTransaction {
                        assertEquals(
                            1,
                            Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value
                        )
                    }
                }

                launchResult.await()
                val result = suspendTransactionAsync(Dispatchers.Default, db = db) {
                    Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value
                }.await()

                val result2 = suspendTransactionAsync(Dispatchers.Default, db = db) {
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
        withTables(TestingUnique) {
            val (originalId, updatedId) = 1 to 99
            val mainJob = GlobalScope.async(Dispatchers.Default) {
                suspendTransaction(Dispatchers.Default, db = db) {
                    TestingUnique.insert { it[id] = originalId }

                    assertEquals(originalId, TestingUnique.selectAll().single()[TestingUnique.id])
                }

                val (insertResult, updateResult) = listOf(
                    suspendTransactionAsync(db = db) {
                        maxAttempts = 20

                        // throws JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation
                        // until original row is updated with a new id
                        TestingUnique.insert { it[id] = originalId }

                        TestingUnique.selectAll().count()
                    },
                    suspendTransactionAsync(db = db) {
                        maxAttempts = 20

                        TestingUnique.update({ TestingUnique.id eq originalId }) { it[id] = updatedId }
                        TestingUnique.selectAll().count()
                    }
                ).awaitAll()

                kotlin.test.assertEquals(1L, updateResult, "Failing at end of update job")
                kotlin.test.assertEquals(2L, insertResult, "Failing at end of insert job")
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEqualCollections(listOf(updatedId, originalId), TestingUnique.selectAll().map { it[TestingUnique.id] })
        }
    }

    @Test
    @RepeatableTest(10)
    fun nestedSuspendTxTest() {
        suspend fun insertTesting(db: R2dbcDatabase) = suspendTransaction(db = db) {
            Testing.insert {}
        }
        withTables(Testing) {
            val mainJob = GlobalScope.async {
                val job = launch(Dispatchers.IO) {
                    suspendTransaction(db = db) {
                        connection.setTransactionIsolation(IsolationLevel.READ_COMMITTED)
                        assertEquals(
                            null,
                            Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)
                        )

                        insertTesting(db)

                        if (currentDialectTest is MysqlDialect) commit()

                        assertEquals(
                            1,
                            Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value
                        )
                    }
                }

                job.join()
                val result = suspendTransaction(Dispatchers.Default, db = db) {
                    Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value
                }

                kotlin.test.assertEquals(1, result)
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(1, Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value)
        }
    }

    @Test
    @RepeatableTest(10)
    fun nestedSuspendAsyncTxTest() {
        withTables(TestDB.ALL_H2_V2, Testing) {
            val mainJob = GlobalScope.async {
                val job = launch(Dispatchers.IO) {
                    suspendTransaction(db = db) {
                        repeat(10) {
                            Testing.insert { }
                        }
                        commit()
                        (1..10).map {
                            suspendTransactionAsync {
                                Testing.selectAll().toList()
                            }
                        }.awaitAll()
                    }
                }

                job.join()
                val result = suspendTransaction(Dispatchers.Default, db = db) {
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
        withTables(Testing) {
            val mainJob = GlobalScope.async {
                val results = (1..5).map { indx ->
                    suspendTransactionAsync(Dispatchers.IO, db = db) {
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
}
