package org.jetbrains.exposed.sql.tests.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.h2.H2Tests
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.test.utils.RepeatableTest
import org.junit.Test

class CoroutineTests : DatabaseTestsBase() {
    @Test @RepeatableTest(10)
    fun suspendedTx() {
        withDb {
            runBlocking {
                SchemaUtils.create(H2Tests.Testing)

                val launchResult = suspendedTransaction {
                    H2Tests.Testing.insert{}

                    launch(Dispatchers.Default) {
                        suspendedTransaction {
                            assertEquals(1, H2Tests.Testing.select { H2Tests.Testing.id.eq(1) }.singleOrNull()?.getOrNull(H2Tests.Testing.id))
                        }
                    }
                }

                val result = suspendedTransaction(Dispatchers.Default) {
                    H2Tests.Testing.select { H2Tests.Testing.id.eq(1) }.single()[H2Tests.Testing.id]
                }

                assertEquals(1, result)
                launchResult.join()
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
                    }
                }

                val result = suspendedTransactionAsync(Dispatchers.Default) {
                    H2Tests.Testing.select { H2Tests.Testing.id.eq(1) }.single()[H2Tests.Testing.id]
                }

                launchResult.await().result.join()
                assertEquals(1, result.await().result)
                SchemaUtils.drop(H2Tests.Testing)
            }
        }
    }
    
}