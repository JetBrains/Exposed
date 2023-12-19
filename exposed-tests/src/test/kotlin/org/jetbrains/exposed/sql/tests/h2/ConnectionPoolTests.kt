package org.jetbrains.exposed.sql.tests.h2

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.LogDbInTestName
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertFalse
import org.junit.Assume
import org.junit.Test
import java.sql.Connection
import kotlin.test.assertNotNull

class ConnectionPoolTests : LogDbInTestName() {
    private val hikariDataSource1 by lazy {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:hikariDB1"
                maximumPoolSize = 10
            }
        )
    }

    private val hikariDB1 by lazy {
        Database.connect(hikariDataSource1)
    }

    @Test
    fun testSuspendTransactionsExceedingPoolSize() {
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledDialects())
        transaction(db = hikariDB1) {
            SchemaUtils.create(TestTable)
        }

        val exceedsPoolSize = (hikariDataSource1.maximumPoolSize * 2 + 1).coerceAtMost(50)
        runBlocking {
            repeat(exceedsPoolSize) {
                launch {
                    newSuspendedTransaction {
                        delay(100)
                        TestEntity.new { testValue = "test$it" }
                    }
                }
            }
        }

        transaction(db = hikariDB1) {
            assertEquals(exceedsPoolSize, TestEntity.all().toList().count())

            SchemaUtils.drop(TestTable)
        }
    }

    private val hikariDataSourcePG by lazy {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = TestDB.POSTGRESQL.connection.invoke()
                username = TestDB.POSTGRESQL.user
                password = TestDB.POSTGRESQL.pass
                // sets the default schema for connections, which opens a database transaction before Exposed does
                schema = "test_schema"
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

        // setting schema directly in hikari config should not throw exception when Exposed creates
        // a new transaction and checks if connection parameters need to be reset
        transaction(db = hikariPG) {
            val foundHikariSchema = exec("SELECT schema_name FROM information_schema.schemata;") {
                var match = false
                while (it.next()) {
                    if (it.getString(1) == "test_schema") {
                        match = true
                        break
                    }
                }
                match
            }
            assertNotNull(foundHikariSchema)
            assertTrue(foundHikariSchema)
        }

        TransactionManager.closeAndUnregister(hikariPG)
    }

    @Test
    fun testReadOnlyModeWithHikariAndPostgres() {
        Assume.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())

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
                SchemaUtils.create(TestTable)
            }
        }

        // transaction setting should override hikari config
        transaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, readOnly = false, db = hikariPG) {
            assertFalse(getReadOnlyMode())

            // table can now be created and dropped
            SchemaUtils.create(TestTable)
            SchemaUtils.drop(TestTable)
        }

        TransactionManager.closeAndUnregister(hikariPG)
    }

    object TestTable : IntIdTable("HIKARI_TESTER") {
        val testValue = varchar("test_value", 32)
    }

    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<TestEntity>(TestTable)

        var testValue by TestTable.testValue
    }
}
