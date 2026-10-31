/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.exposed.v1.tests.postgresql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
import org.jetbrains.exposed.v1.tests.LogDbInTestName
import org.jetbrains.exposed.v1.tests.NOT_APPLICABLE_TO_R2DBC
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Tag(NOT_APPLICABLE_TO_R2DBC)
class DataSourceTransactionSettingsTest : LogDbInTestName() {
    @Test
    fun testDefaultTransactionUsesHikariIsolationWhenAnotherTransactionConnectsFirst() {
        withHikariDatabase { db ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                transaction(db) {
                    executor.submit<Int> {
                        transaction(db) { connection.transactionIsolation }
                    }.get(10, TimeUnit.SECONDS)

                    assertEquals("repeatable read", setting("transaction_isolation"))
                    assertEquals(Connection.TRANSACTION_REPEATABLE_READ, transactionIsolation)
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun testRequestedIsolationIsAppliedWhenTransactionOpensFirstConnection() {
        withHikariDatabase { db ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                transaction(db) {
                    val isolation = executor.submit<String> {
                        transaction(db, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
                            setting("transaction_isolation")
                        }
                    }.get(10, TimeUnit.SECONDS)

                    assertEquals("read committed", isolation)
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun testReadOnlyModeWithHikariAppliesToEveryTransaction() {
        withHikariDatabase(readOnly = true) { db ->
            repeat(2) {
                transaction(db) {
                    assertEquals("on", setting("transaction_read_only"))
                }
            }
        }
    }

    @Test
    fun testExplicitReadOnlyOverridesHikariOnFirstTransaction() {
        withHikariDatabase(readOnly = true) { db ->
            transaction(db, readOnly = false) {
                assertEquals("off", setting("transaction_read_only"))
            }
            transaction(db) {
                assertEquals("on", setting("transaction_read_only"))
            }
        }
    }

    @Test
    fun testConfiguredReadOnlyOverridesHikariOnFirstTransaction() {
        withHikariDatabase(DatabaseConfig { defaultReadOnly = false }, readOnly = true) { db ->
            repeat(2) {
                transaction(db) {
                    assertEquals("off", setting("transaction_read_only"))
                }
            }
            transaction(db, readOnly = true) {
                assertEquals("on", setting("transaction_read_only"))
            }
        }
    }

    @Test
    fun testConfiguredReadOnlyTrueOverridesWritableHikariConnections() {
        withHikariDatabase(DatabaseConfig { defaultReadOnly = true }) { db ->
            repeat(2) {
                transaction(db) {
                    assertEquals("on", setting("transaction_read_only"))
                }
            }
        }
    }

    @Test
    fun testManagerReadOnlyOverridesHikariDefault() {
        withHikariDatabase(readOnly = true) { db ->
            db.transactionManager.defaultReadOnly = false
            transaction(db) {
                assertEquals("off", setting("transaction_read_only"))
            }
        }
    }

    @Test
    fun testSuspendTransactionsInheritHikariSettings() {
        withHikariDatabase(readOnly = true) { db ->
            runBlocking {
                repeat(2) {
                    suspendTransaction(db) {
                        assertEquals("repeatable read", setting("transaction_isolation"))
                        assertEquals("on", setting("transaction_read_only"))
                        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, transactionIsolation)
                        assertTrue(readOnly)
                    }
                }
            }
        }
    }

    @Test
    fun testNestedTransactionsInheritHikariSettings() {
        withHikariDatabase(DatabaseConfig { useNestedTransactions = true }, readOnly = true) { db ->
            transaction(db) {
                transaction(db) {
                    assertEquals("repeatable read", setting("transaction_isolation"))
                    assertEquals("on", setting("transaction_read_only"))
                    assertEquals(Connection.TRANSACTION_REPEATABLE_READ, transactionIsolation)
                    assertTrue(readOnly)
                }
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, transactionIsolation)
                assertTrue(readOnly)
            }
        }
    }

    @Test
    fun testNestedSuspendTransactionsInheritHikariSettings() {
        withHikariDatabase(DatabaseConfig { useNestedTransactions = true }, readOnly = true) { db ->
            runBlocking {
                suspendTransaction(db) {
                    suspendTransaction(db) {
                        assertEquals("repeatable read", setting("transaction_isolation"))
                        assertEquals("on", setting("transaction_read_only"))
                        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, transactionIsolation)
                        assertTrue(readOnly)
                    }
                }
            }
        }
    }

    @Test
    fun testManagerIsolationOverrideAfterLoadingPoolDefaults() {
        withHikariDatabase { db ->
            transaction(db) {
                assertEquals("repeatable read", setting("transaction_isolation"))
            }
            db.transactionManager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            transaction(db) {
                assertEquals("serializable", setting("transaction_isolation"))
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, db.transactionManager.defaultIsolationLevel)
            }
            db.transactionManager.defaultIsolationLevel = -1
            transaction(db) {
                assertEquals("repeatable read", setting("transaction_isolation"))
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, db.transactionManager.defaultIsolationLevel)
            }
        }
    }

    @Test
    fun testBuilderReadOnlyChangesBeforeConnectingArePreserved() {
        lateinit var builder: DatabaseConfig.Builder
        val config = DatabaseConfig { builder = this }
        builder.defaultReadOnly = false
        withHikariDatabase(config, readOnly = true) { db ->
            transaction(db) {
                assertEquals("off", setting("transaction_read_only"))
            }
        }
    }

    @Test
    fun testFirstPropertyReadReturnsSettingsResolvedDuringDialectDiscovery() {
        withHikariDatabase(readOnly = true) { db ->
            transaction(db) {
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, transactionIsolation)
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.transactionIsolation)
                assertTrue(readOnly)
            }
        }
    }

    @Test
    fun testCurrentOrNewInheritsHikariSettingsBeforeAndAfterCaching() {
        withHikariDatabase(readOnly = true) { db ->
            repeat(2) {
                val transaction = TransactionManager.currentOrNew()
                try {
                    assertEquals(Connection.TRANSACTION_REPEATABLE_READ, transaction.connection.transactionIsolation)
                    assertTrue(transaction.connection.readOnly)
                    assertEquals(Connection.TRANSACTION_REPEATABLE_READ, transaction.transactionIsolation)
                    assertTrue(transaction.readOnly)
                } finally {
                    transaction.close()
                }
            }
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ, db.transactionManager.defaultIsolationLevel)
            assertTrue(db.transactionManager.defaultReadOnly)
        }
    }

    @Test
    fun testCurrentOrNewPreservesExplicitIsolationAndReusesCurrentTransaction() {
        withHikariDatabase(readOnly = true) { db ->
            val transaction = TransactionManager.currentOrNew(Connection.TRANSACTION_SERIALIZABLE)
            try {
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, transaction.connection.transactionIsolation)
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, transaction.transactionIsolation)
                assertTrue(transaction.connection.readOnly)
                assertTrue(transaction.readOnly)
            } finally {
                transaction.close()
            }
            transaction(db) {
                assertSame(this, TransactionManager.currentOrNew())
                assertSame(this, TransactionManager.currentOrNew(Connection.TRANSACTION_READ_COMMITTED))
            }
        }
    }

    @Test
    fun testDirectTransactionPreservesExplicitSettingsOnFirstConnection() {
        withHikariDatabase(readOnly = true) { db ->
            val transaction = db.transactionManager.newTransaction(Connection.TRANSACTION_SERIALIZABLE, readOnly = false)
            try {
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, transaction.connection.transactionIsolation)
                assertFalse(transaction.connection.readOnly)
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, transaction.transactionIsolation)
                assertFalse(transaction.readOnly)
            } finally {
                transaction.close()
            }
        }
    }

    @Test
    fun testDirectTransactionResolvesDefaultIsolationAndPreservesExplicitReadOnly() {
        withHikariDatabase(readOnly = true) { db ->
            val transaction = db.transactionManager.newTransaction(isolation = -1, readOnly = false)
            try {
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, transaction.connection.transactionIsolation)
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, transaction.transactionIsolation)
                assertFalse(transaction.connection.readOnly)
                assertFalse(transaction.readOnly)
            } finally {
                transaction.close()
            }
        }
    }

    @Test
    fun testDirectTransactionUsesResolvedManagerDefaults() {
        withHikariDatabase(readOnly = true) { db ->
            val first = db.transactionManager.newTransaction()
            try {
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, first.connection.transactionIsolation)
                assertFalse(first.connection.readOnly)
            } finally {
                first.close()
            }
            val next = db.transactionManager.newTransaction()
            try {
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, next.connection.transactionIsolation)
                assertTrue(next.connection.readOnly)
            } finally {
                next.close()
            }
        }
    }

    private fun withHikariDatabase(
        databaseConfig: DatabaseConfig = DatabaseConfig {},
        readOnly: Boolean = false,
        block: (Database) -> Unit
    ) {
        Assumptions.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())
        val config = HikariConfig().apply {
            jdbcUrl = TestDB.POSTGRESQL.connection()
            username = TestDB.POSTGRESQL.user
            password = TestDB.POSTGRESQL.pass
            maximumPoolSize = 2
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            isReadOnly = readOnly
        }
        HikariDataSource(config).use { dataSource ->
            val db = Database.connect(dataSource, databaseConfig = databaseConfig)
            val previousDefault = TransactionManager.defaultDatabase
            TransactionManager.defaultDatabase = db
            try {
                block(db)
            } finally {
                TransactionManager.defaultDatabase = previousDefault
                TransactionManager.closeAndUnregister(db)
            }
        }
    }

    private fun JdbcTransaction.setting(name: String): String = exec("SHOW $name") {
        it.next()
        it.getString(1)
    }!!
}
