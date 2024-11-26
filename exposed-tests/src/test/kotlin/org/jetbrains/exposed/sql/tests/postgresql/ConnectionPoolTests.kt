package org.jetbrains.exposed.sql.tests.postgresql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.tests.LogDbInTestName
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.Assume
import org.junit.Test
import java.sql.Connection
import kotlin.test.assertNotNull

class ConnectionPoolTests : LogDbInTestName() {
    private val hikariDataSourcePG by lazy {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = TestDB.POSTGRESQL.connection.invoke()
                username = TestDB.POSTGRESQL.user
                password = TestDB.POSTGRESQL.pass
                // sets the default schema for connections, which opens a database transaction before Exposed does
                schema = "public"
                maximumPoolSize = 10
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_SERIALIZABLE"
                isReadOnly = true
            }
        )
    }

    private val hikariPG by lazy {
        Database.connect(hikariDataSourcePG)
    }

    @Test
    fun testSchemaAndConnectionsWithHikariAndPostgresql() {
        Assume.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

        // setting default schema directly in hikari config should not throw exception when Exposed creates
        // a new transaction and checks if connection parameters need to be reset
        transaction(db = hikariPG) {
            val schema = exec("SELECT CURRENT_SCHEMA;") {
                it.next()
                it.getString(1)
            }
            assertEquals("public", schema)
        }

        TransactionManager.closeAndUnregister(hikariPG)
    }

    @Test
    fun testReadOnlyModeWithHikariAndPostgres() {
        Assume.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

        val testTable = object : IntIdTable("HIKARI_TESTER") { }

        fun Transaction.getReadOnlyMode(): Boolean {
            val mode = exec("SHOW transaction_read_only;") {
                it.next()
                it.getBoolean(1)
            }
            assertNotNull(mode)
            return mode
        }

        // read only mode should be set directly by hikari config
        transaction(db = hikariPG) {
            assertTrue(getReadOnlyMode())

            // table cannot be created in read-only mode
            expectException<ExposedSQLException> {
                SchemaUtils.create(testTable)
            }
        }

        // transaction setting should override hikari config
        transaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, readOnly = false, db = hikariPG) {
            Assert.assertFalse(getReadOnlyMode())

            // table can now be created and dropped
            SchemaUtils.create(testTable)
            SchemaUtils.drop(testTable)
        }

        TransactionManager.closeAndUnregister(hikariPG)
    }

    @Test
    fun testSuspendedReadOnlyModeWithHikariAndPostgres() = runTest {
        Assume.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

        val testTable = object : IntIdTable("HIKARI_TESTER") { }

        fun Transaction.getReadOnlyMode(): Boolean {
            val mode = exec("SHOW transaction_read_only;") {
                it.next()
                it.getBoolean(1)
            }
            assertNotNull(mode)
            return mode
        }

        // read only mode should be set directly by hikari config
        newSuspendedTransaction(db = hikariPG) {
            assertTrue(getReadOnlyMode())

            // table cannot be created in read-only mode
            expectException<ExposedSQLException> {
                SchemaUtils.create(testTable)
            }
        }

        // transaction setting should override hikari config
        newSuspendedTransaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, readOnly = false, db = hikariPG) {
            Assert.assertFalse(getReadOnlyMode())

            // table can now be created and dropped
            SchemaUtils.create(testTable)
            SchemaUtils.drop(testTable)
        }

        TransactionManager.closeAndUnregister(hikariPG)
    }
}
