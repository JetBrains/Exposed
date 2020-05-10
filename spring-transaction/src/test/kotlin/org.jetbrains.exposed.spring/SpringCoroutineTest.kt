package org.jetbrains.exposed.spring

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.tests.RepeatableTest
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
    @Test @Transactional @Commit
    open fun testNestedCoroutineTransaction() {
        try {
            SchemaUtils.create(Testing)

            val mainJob = GlobalScope.async {

                val results = (1..5).map { indx ->
                    suspendedTransactionAsync(Dispatchers.IO) {
                        Testing.insert {  }
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
        }*/
    }
}