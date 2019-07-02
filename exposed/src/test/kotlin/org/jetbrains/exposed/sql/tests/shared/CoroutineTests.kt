package org.jetbrains.exposed.sql.tests.shared

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.h2.H2Tests
import org.jetbrains.exposed.sql.transactions.experimental.andThen
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.test.utils.RepeatableTest
import org.junit.Rule
import org.junit.Test

class CoroutineTests : DatabaseTestsBase() {

    @Rule
    @JvmField
    val timeout = CoroutinesTimeout.seconds(100)

    @Test @RepeatableTest(10)
    fun suspendedTx() {
        withDb {
            runBlocking {
                SchemaUtils.create(H2Tests.Testing)
                commit()

                val job = newSuspendedTransaction(Dispatchers.IO) {
                    H2Tests.Testing.insert{}

                    launch(Dispatchers.Default) {
                        suspendedTransaction {
                            assertEquals(1, H2Tests.Testing.select { H2Tests.Testing.id.eq(1) }.singleOrNull()?.getOrNull(H2Tests.Testing.id))
                        }
                    }
                }


                val result = suspendedTransaction(Dispatchers.Default) {
                    job.join()
                    H2Tests.Testing.select { H2Tests.Testing.id.eq(1) }.single()[H2Tests.Testing.id]
                }

                assertEquals(1, result)

                SchemaUtils.drop(H2Tests.Testing)
            }
        }
    }

    @Test @RepeatableTest(10)
    fun suspendTxAsync() {
        withDb {
            runBlocking {
                SchemaUtils.create(H2Tests.Testing)

                val launchResult = suspendedTransactionAsync {
                    H2Tests.Testing.insert{}

                    launch(Dispatchers.Default) {
                        suspendedTransaction {
                            assertEquals(1, H2Tests.Testing.select { H2Tests.Testing.id.eq(1) }.singleOrNull()?.getOrNull(H2Tests.Testing.id))
                        }
                        commit()
                    }
                }

                val result = suspendedTransactionAsync(Dispatchers.Default, useOuterTransactionIfAccessible = true) {
                    launchResult.await().join()
                    H2Tests.Testing.select { H2Tests.Testing.id.eq(1) }.single()[H2Tests.Testing.id]
                }.andThen {
                    assertEquals(1, it)
                    true
                }

                assertEquals(true, result.await())
                SchemaUtils.drop(H2Tests.Testing)
            }
        }
    }
}