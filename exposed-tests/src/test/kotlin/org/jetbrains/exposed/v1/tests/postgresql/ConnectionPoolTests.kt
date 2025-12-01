package org.jetbrains.exposed.v1.tests.postgresql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.LogDbInTestName
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.sql.Connection

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
        Assumptions.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

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
        Assumptions.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

        // read only mode should be set directly by hikari config
        transaction(db = hikariPG) {
            assertTrue(getReadOnlyMode())

            // table cannot be created in read-only mode
            expectException<ExposedSQLException> {
                SchemaUtils.create(TestTable)
            }
        }

        // transaction setting should override hikari config
        transaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, readOnly = false, db = hikariPG) {
            Assumptions.assumeFalse(getReadOnlyMode())

            // table can now be created and dropped
            SchemaUtils.create(TestTable)
            SchemaUtils.drop(TestTable)
        }

        TransactionManager.closeAndUnregister(hikariPG)
    }

    @Test
    fun testSuspendedReadOnlyModeWithHikariAndPostgres() = runTest {
        Assumptions.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

        val testTable = object : IntIdTable("HIKARI_TESTER") { }

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
            Assumptions.assumeFalse(getReadOnlyMode())

            // table can now be created and dropped
            SchemaUtils.create(testTable)
            SchemaUtils.drop(testTable)
        }

        TransactionManager.closeAndUnregister(hikariPG)
    }
}

private val TestTable = object : IntIdTable("HIKARI_TESTER") { }

private fun JdbcTransaction.getReadOnlyMode(): Boolean {
    val mode = exec("SHOW transaction_read_only;") {
        it.next()
        it.getBoolean(1)
    }
    assertNotNull(mode)
    return mode == true
}
