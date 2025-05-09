package org.jetbrains.exposed.v1.spring.transaction

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.RepeatableTest
import org.junit.Rule
import org.junit.Test
import org.springframework.test.annotation.Commit
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

open class SpringCoroutineTest : SpringTransactionTestBase() {

    @Rule
    @JvmField
    val timeout = CoroutinesTimeout.seconds(60)

    object Testing : Table("COROUTINE_TESTING") {
        val id = integer("id").autoIncrement() // Column<Int>

        override val primaryKey = PrimaryKey(id)
    }

    @RepeatableTest(times = 5)
    @Test
    @Transactional
    @Commit
    // Is this test flaky?
    open fun testNestedCoroutineTransaction() {
        try {
            SchemaUtils.create(Testing)

            val mainJob = GlobalScope.async {
                val results = (1..5).map { indx ->
                    suspendedTransactionAsync(Dispatchers.IO) {
                        Testing.insert { }
                        indx
                    }
                }.awaitAll()

                assertEquals(15, results.sum())
            }

            while (!mainJob.isCompleted) Thread.sleep(100)
            mainJob.getCompletionExceptionOrNull()?.let { throw it }

            transaction {
                assertEquals(5L, Testing.selectAll().count())
            }
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
