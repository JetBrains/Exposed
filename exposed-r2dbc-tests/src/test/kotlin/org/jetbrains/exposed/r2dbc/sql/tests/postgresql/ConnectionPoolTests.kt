package org.jetbrains.exposed.r2dbc.sql.tests.postgresql

import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Option
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.r2dbc.sql.R2dbcDatabase
import org.jetbrains.exposed.r2dbc.sql.tests.LogDbInTestName
import org.jetbrains.exposed.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.r2dbc.sql.tests.getString
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertEquals
import org.jetbrains.exposed.r2dbc.sql.transactions.TransactionManager
import org.jetbrains.exposed.r2dbc.sql.transactions.suspendTransaction
import org.junit.Assume
import org.junit.Test

class ConnectionPoolTests : LogDbInTestName() {
    private val poolPG by lazy {
        R2dbcDatabase.connect {
            defaultR2dbcIsolationLevel = IsolationLevel.SERIALIZABLE

            setUrl(TestDB.POSTGRESQL.connection.invoke().replace("rdbc:", "r2dbc:pool:"))

            connectionFactoryOptions {
                option(ConnectionFactoryOptions.USER, TestDB.POSTGRESQL.user)
                option(ConnectionFactoryOptions.PASSWORD, TestDB.POSTGRESQL.pass)
                option(Option.valueOf("maxSize"), 10)
                option(Option.valueOf("schema"), "public")
            }
        }
    }

    // NOTE: DIFFERENT NAME
    @Test
    fun testSchemaAndConnectionsWithPoolAndPostgresql() = runTest {
        Assume.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

        // setting default schema directly in connection options should not throw exception when Exposed creates
        // a new transaction and checks if connection parameters need to be reset
        suspendTransaction(db = poolPG) {
            val schema = exec("SELECT CURRENT_SCHEMA;") {
                it.getString(1)
            }?.single()
            assertEquals("public", schema)
        }

        TransactionManager.closeAndUnregister(poolPG)
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

// private val TestTable = object : IntIdTable("POOL_TESTER") { }

// private suspend fun R2dbcTransaction.getReadOnlyMode(): Boolean {
//    val mode = exec("SHOW transaction_read_only;") {
//        it.getBoolean(1)
//    }?.single()
//    assertNotNull(mode)
//    return mode == true
// }
