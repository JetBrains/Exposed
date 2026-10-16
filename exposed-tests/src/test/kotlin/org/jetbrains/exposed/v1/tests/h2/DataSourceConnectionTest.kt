/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.exposed.v1.tests.h2

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.JdbcTransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.DataSourceStub
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.SQLTransientException
import kotlin.test.*

class DataSourceConnectionTest {
    @Test
    fun testTransactionSettingsDoNotAcquireExtraConnections() {
        withDataSource { db, dataSource ->
            val empty = transaction(db) {
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, transactionIsolation)
                assertFalse(readOnly)
                this
            }
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, empty.transactionIsolation)
            assertFalse(empty.readOnly)
            assertTrue(dataSource.connections.isEmpty())

            transaction(db) {
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.transactionIsolation)
            }
            assertEquals(1, dataSource.connections.size)
            assertTrue(dataSource.connections.single().isClosed)
        }
    }

    @Test
    fun testRetriesUseOneConnectionPerAttempt() {
        withDataSource { db, dataSource ->
            val failure = SQLTransientException("retry")
            val thrown = assertFailsWith<SQLTransientException> {
                transaction(db) {
                    maxAttempts = 3
                    connection
                    throw failure
                }
            }
            assertSame(failure, thrown)
            assertEquals(3, dataSource.connections.size)
            assertTrue(dataSource.connections.all { it.isClosed })
        }
    }

    @Test
    fun testFailedSetupClosesConnectionAndPreservesCloseFailure() {
        val failure = SQLException("read-only setup")
        val closeFailure = SQLException("close")
        withDataSource(decorate = { delegate ->
            object : Connection by delegate {
                override fun setReadOnly(readOnly: Boolean) {
                    throw failure
                }

                override fun close() {
                    delegate.close()
                    throw closeFailure
                }
            }
        }) { db, dataSource ->
            val thrown = assertFailsWith<SQLException> {
                transaction(db, readOnly = true) { connection }
            }
            assertSame(failure, thrown)
            assertEquals(listOf(closeFailure), thrown.suppressed.toList())
            assertEquals(1, dataSource.connections.size)
            assertTrue(dataSource.connections.single().isClosed)
        }
    }

    @Test
    fun testCustomConnectionSetupRetainsControl() {
        var setupCalls = 0
        withDataSource(manager = { db ->
            TransactionManager(db) { connection, transaction ->
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, transaction.transactionIsolation)
                assertTrue(transaction.readOnly)
                connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
                connection.autoCommit = false
                setupCalls++
            }
        }) { db, dataSource ->
            transaction(db, transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, readOnly = true) {
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.transactionIsolation)
            }
            assertEquals(1, setupCalls)
            assertEquals(1, dataSource.connections.size)
        }
    }

    @Test
    fun testCustomManagerReceivesResolvedSettings() {
        var managerCalls = 0
        withDataSource(manager = { db ->
            val delegate = TransactionManager(db)
            object : JdbcTransactionManager by delegate {
                override fun newTransaction(isolation: Int, readOnly: Boolean, outerTransaction: JdbcTransaction?): JdbcTransaction {
                    assertEquals(Connection.TRANSACTION_READ_COMMITTED, isolation)
                    assertFalse(readOnly)
                    managerCalls++
                    return delegate.newTransaction(isolation, readOnly, outerTransaction)
                }
            }
        }) { db, dataSource ->
            transaction(db) { connection }
            assertEquals(1, managerCalls)
            assertEquals(1, dataSource.connections.size)
        }
    }

    private fun withDataSource(
        decorate: (Connection) -> Connection = { it },
        manager: (Database) -> JdbcTransactionManager = { TransactionManager(it) },
        block: (Database, CountingDataSource) -> Unit
    ) {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        Class.forName(TestDB.H2_V2.driver)
        val dataSource = CountingDataSource(decorate)
        val db = Database.connect(
            dataSource,
            databaseConfig = DatabaseConfig {
                explicitDialect = H2Dialect()
                defaultMaxAttempts = 1
            },
            manager = manager
        )
        try {
            block(db, dataSource)
        } finally {
            dataSource.connections.filterNot { it.isClosed }.forEach { it.close() }
            TransactionManager.closeAndUnregister(db)
        }
    }

    private class CountingDataSource(private val decorate: (Connection) -> Connection) : DataSourceStub() {
        val connections = mutableListOf<Connection>()

        override fun getConnection(): Connection {
            val connection = DriverManager.getConnection(TestDB.H2_V2.connection(), TestDB.H2_V2.user, TestDB.H2_V2.pass)
            connections.add(connection)
            return decorate(connection)
        }
    }
}
