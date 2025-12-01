package org.jetbrains.exposed.v1.r2dbc.sql.tests.postgresql

import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Option
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.LogDbInTestName
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.getString
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

class ConnectionPoolTests : LogDbInTestName() {
    private val maximumPoolSize = 10

    private val poolPG by lazy {
        R2dbcDatabase.connect {
            defaultR2dbcIsolationLevel = IsolationLevel.SERIALIZABLE

            setUrl(TestDB.POSTGRESQL.connection.invoke().replaceUrlAsPool())

            connectionFactoryOptions {
                option(ConnectionFactoryOptions.USER, TestDB.POSTGRESQL.user)
                option(ConnectionFactoryOptions.PASSWORD, TestDB.POSTGRESQL.pass)
                option(Option.valueOf("maxSize"), maximumPoolSize)
                option(Option.valueOf("schema"), "public")
            }
        }
    }

    private val poolPGManual by lazy {
        val adjustedUrl = TestDB.POSTGRESQL.connection.invoke()
            .replaceUrlAsPool()
            .plus("&maxSize=$maximumPoolSize")

        R2dbcDatabase.connect(url = adjustedUrl)
    }

    private fun String.replaceUrlAsPool(): String = replace("r2dbc:", "r2dbc:pool:")

    // NOTE: DIFFERENT NAME
    @Test
    fun testSchemaAndConnectionsWithPoolAndPostgresql() = runTest {
        Assumptions.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

        // setting default schema directly in connection options should not throw exception when Exposed creates
        // a new transaction and checks if connection parameters need to be reset
        suspendTransaction(poolPG) {
            val schema = exec("SELECT CURRENT_SCHEMA;") {
                it.getString(1)
            }?.single()
            assertEquals("public", schema)
        }

        TransactionManager.closeAndUnregister(poolPG)
    }

    @Test
    fun testSuspendTransactionsExceedingPoolSize() = runTest {
        Assumptions.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

        val singleId = 99

        suspendTransaction(poolPGManual) {
            SchemaUtils.create(TestTable)
            TestTable.insert {
                it[id] = singleId
                it[amount] = 0
            }
        }

        val exceedsPoolSize = (maximumPoolSize * 2 + 1).coerceAtMost(50)
        List(exceedsPoolSize) {
            launch(Dispatchers.IO) {
                suspendTransaction(poolPGManual) {
                    val current = TestTable
                        .selectAll()
                        .where { TestTable.id eq singleId }
                        .forUpdate()
                        .single()[TestTable.amount]

                    delay(100)

                    TestTable.update({ TestTable.id eq singleId }) {
                        it[amount] = current + 1
                    }
                }
            }
        }.joinAll()

        suspendTransaction(poolPGManual) {
            val result = TestTable.selectAll().first()
            assertEquals(exceedsPoolSize, result[TestTable.amount])

            SchemaUtils.drop(TestTable)
        }

        TransactionManager.closeAndUnregister(poolPGManual)
    }

    // NOTE: NO RELEVANCE
    // there is no connection option for read only, only afterwards from beginTransaction()
//    @Test
//    fun testReadOnlyModeWithHikariAndPostgres() = runTest {
//        Assume.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())
//
//        // read only mode should be set directly by pool
//        suspendTransaction(db = poolPG) {
//            assertTrue(getReadOnlyMode())
//
//            // table cannot be created in read-only mode
//            expectException<ExposedR2dbcException> {
//                SchemaUtils.create(TestTable)
//            }
//        }
//
//        // transaction setting should override hikari config
//        suspendTransaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, readOnly = false, db = poolPG) {
//            Assert.assertFalse(getReadOnlyMode())
//
//            // table can now be created and dropped
//            SchemaUtils.create(TestTable)
//            SchemaUtils.drop(TestTable)
//        }
//
//        TransactionManager.closeAndUnregister(poolPG)
//    }
}

private val TestTable = object : IntIdTable("POOL_TESTER") {
    val amount = integer("amount")
}

// private suspend fun R2dbcTransaction.getReadOnlyMode(): Boolean {
//    val mode = exec("SHOW transaction_read_only;") {
//        it.getBoolean(1)
//    }?.single()
//    assertNotNull(mode)
//    return mode == true
// }
