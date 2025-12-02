package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit5.CoroutinesTimeout
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.Executors

private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

// equivalent to exposed-tests/CoroutineTests.kt
// Note that all tests are aligned equivalents of the JDBC versions, meaning that:
// every newSuspendedTransaction(Dispatcher) --> withContext(Dispatcher) { inTopLevelSuspendTransaction() };
// Tests still pass even if context is not wrapped, meaning: newSuspendedTransaction(Dispatcher) --> inTopLevelSuspendTransaction(),
// since the dispatcher originally passed never differs from the outer scope context in which transaction method is called;
// This may be a leniency of the tests however, as neither these nor the original tests actually test for the current context being used,
// just the consequences of the database operations.
@ExperimentalCoroutinesApi
@DelicateCoroutinesApi
class CoroutineTests : R2dbcDatabaseTestsBase() {

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
                    inTopLevelSuspendTransaction(db = db) {
                        Testing.insert {}

                        suspendTransaction {
                            assertEquals(1, Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value)
                        }
                    }
                }

                job.join()
                val result = withContext(singleThreadDispatcher) {
                    inTopLevelSuspendTransaction(db = db) {
                        Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value
                    }
                }

                kotlin.test.assertEquals(1, result)
            }

            while (!mainJob.isCompleted) delay(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(1, Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value)
        }
    }

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
    fun testSuspendTransactionWithRepetition() {
        withTables(TestingUnique) { _ ->
            val (originalId, updatedId) = 1 to 99
            val mainJob = GlobalScope.async(Dispatchers.Default) {
                withContext(Dispatchers.Default) {
                    inTopLevelSuspendTransaction(db = db) {
                        TestingUnique.insert { it[id] = originalId }

                        assertEquals(originalId, TestingUnique.selectAll().single()[TestingUnique.id])
                    }
                }

                val insertJob = launch(Dispatchers.Default) {
                    inTopLevelSuspendTransaction(db = db) {
                        maxAttempts = 20

                        // throws JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation
                        // until original row is updated with a new id
                        TestingUnique.insert { it[id] = originalId }

                        assertEquals(2, TestingUnique.selectAll().count())
                    }
                }
                val updateJob = launch(Dispatchers.Default) {
                    inTopLevelSuspendTransaction(db = db) {
                        maxAttempts = 20

                        TestingUnique.update({ TestingUnique.id eq originalId }) { it[id] = updatedId }

                        assertEquals(updatedId, TestingUnique.selectAll().single()[TestingUnique.id])
                    }
                }
                insertJob.join()
                updateJob.join()

                val result = withContext(Dispatchers.Default) {
                    inTopLevelSuspendTransaction(db = db) { TestingUnique.selectAll().count() }
                }
                kotlin.test.assertEquals(2, result, "Failing at end of mainJob")
            }

            while (!mainJob.isCompleted) delay(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEqualCollections(listOf(updatedId, originalId), TestingUnique.selectAll().map { it[TestingUnique.id] })
        }
    }

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
    fun suspendTxAsync() {
        withTables(Testing) {
            val job = GlobalScope.async {
                val launchResult = async(Dispatchers.IO) {
                    inTopLevelSuspendTransaction(db = db) {
                        Testing.insert {}

                        suspendTransaction {
                            assertEquals(
                                1,
                                Testing.selectAll().where { Testing.id.eq(1) }.singleOrNull()?.getOrNull(Testing.id)?.value
                            )
                        }
                    }
                }

                launchResult.await()
                val result = async(Dispatchers.Default) {
                    inTopLevelSuspendTransaction(db = db) {
                        Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value
                    }
                }.await()

                val result2 = async(Dispatchers.Default) {
                    inTopLevelSuspendTransaction(db = db) {
                        assertEquals(1, result)
                        Testing.selectAll().count()
                    }
                }

                kotlin.test.assertEquals(1, result2.await())
            }

            while (!job.isCompleted) delay(100)

            job.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(1, Testing.selectAll().count())
        }
    }

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
    fun testSuspendTransactionAsyncWithRepetition() {
        withTables(TestingUnique) {
            val (originalId, updatedId) = 1 to 99
            val mainJob = GlobalScope.async(Dispatchers.Default) {
                withContext(Dispatchers.Default) {
                    inTopLevelSuspendTransaction(db = db) {
                        TestingUnique.insert { it[id] = originalId }

                        assertEquals(originalId, TestingUnique.selectAll().single()[TestingUnique.id])
                    }
                }

                val (insertResult, updateResult) = listOf(
                    async {
                        inTopLevelSuspendTransaction(db = db) {
                            maxAttempts = 20

                            // throws JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation
                            // until original row is updated with a new id
                            TestingUnique.insert { it[id] = originalId }

                            TestingUnique.selectAll().count()
                        }
                    },
                    async {
                        inTopLevelSuspendTransaction(db = db) {
                            maxAttempts = 20

                            TestingUnique.update({ TestingUnique.id eq originalId }) { it[id] = updatedId }
                            TestingUnique.selectAll().count()
                        }
                    }
                ).awaitAll()

                kotlin.test.assertEquals(1, updateResult, "Failing at end of update job")
                kotlin.test.assertEquals(2, insertResult, "Failing at end of insert job")
            }

            while (!mainJob.isCompleted) delay(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEqualCollections(listOf(updatedId, originalId), TestingUnique.selectAll().map { it[TestingUnique.id] })
        }
    }

    @Disabled("Until isolation level fixed")
    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
    fun nestedSuspendTxTest() {
        suspend fun insertTesting(db: R2dbcDatabase) = inTopLevelSuspendTransaction(db = db) {
            Testing.insert {}
        }
        withTables(Testing) {
            val mainJob = GlobalScope.async {
                val job = launch(Dispatchers.IO) {
                    inTopLevelSuspendTransaction(db = db, transactionIsolation = IsolationLevel.READ_COMMITTED) {
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
                val result = withContext(Dispatchers.Default) {
                    inTopLevelSuspendTransaction(db = db) {
                        Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value
                    }
                }

                kotlin.test.assertEquals(1, result)
            }

            while (!mainJob.isCompleted) delay(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(1, Testing.selectAll().where { Testing.id.eq(1) }.single()[Testing.id].value)
        }
    }

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
    fun nestedSuspendAsyncTxTest() {
        withTables(listOf(TestDB.H2_V2, TestDB.H2_V2_MYSQL), Testing) {
            val mainJob = GlobalScope.async {
                val job = launch(Dispatchers.IO) {
                    inTopLevelSuspendTransaction(db = db) {
                        repeat(10) {
                            Testing.insert { }
                        }
                        commit()
                        (1..10).map {
                            async {
                                inTopLevelSuspendTransaction {
                                    Testing.selectAll().toList()
                                }
                            }
                        }.awaitAll()
                    }
                }

                job.join()
                val result = withContext(Dispatchers.Default) {
                    inTopLevelSuspendTransaction(db = db) {
                        Testing.selectAll().count()
                    }
                }

                kotlin.test.assertEquals(10, result)
            }

            while (!mainJob.isCompleted) delay(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }
            assertEquals(10, Testing.selectAll().count())
        }
    }

    @RepeatedTest(10)
    @CoroutinesTimeout(60000)
    fun awaitAllTest() {
        withTables(Testing) {
            val mainJob = GlobalScope.async {
                val results = (1..5).map { indx ->
                    async(Dispatchers.IO) {
                        inTopLevelSuspendTransaction(db = db) {
                            Testing.insert { }
                            indx
                        }
                    }
                }.awaitAll()

                kotlin.test.assertEquals(15, results.sum())
            }

            while (!mainJob.isCompleted) delay(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }

            assertEquals(5L, Testing.selectAll().count())
        }
    }
}
