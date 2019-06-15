package org.jetbrains.exposed.sql.tests.mysql

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
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import org.junit.Test
import kotlin.test.assertFalse

class MysqlTests : DatabaseTestsBase() {

    @Test
    fun testEmbeddedConnection() {
        withDb(TestDB.MYSQL) {
            assertFalse(TransactionManager.current().exec("SELECT VERSION();") { it.next(); it.getString(1) }.isNullOrEmpty())
        }
    }

    @Test fun suspendedTx() {
        withDb(TestDB.MYSQL) {
            runBlocking {
                SchemaUtils.create(H2Tests.Testing)

                suspendedTransaction {
                    H2Tests.Testing.insert {
                        it[id] = 1
                    }

                    launch(Dispatchers.Default) {
                        suspendedTransaction {
                            assertEquals(1, H2Tests.Testing.select { H2Tests.Testing.id.eq(1) }.singleOrNull()?.getOrNull(H2Tests.Testing.id))
                        }
                    }
                }

                val result = suspendedTransaction(Dispatchers.Default) {
                    H2Tests.Testing.select { H2Tests.Testing.id.eq(1) }.single()[H2Tests.Testing.id]
                }

                kotlin.test.assertEquals(1, result)
            }
        }
    }
}