package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.After
import org.junit.Assume
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.SQLTransientException
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ConnectionExceptions {

    abstract class ConnectionSpy(private val connection: Connection) : Connection by connection {
        var commitCalled = false
        var rollbackCalled = false
        var closeCalled = false

        override fun commit() {
            commitCalled = true
            throw CommitException()
        }

        override fun rollback() {
            rollbackCalled = true
        }

        override fun close() {
            closeCalled = true
        }
    }

    private class WrappingDataSource<T : Connection>(private val testDB: TestDB, private val connectionDecorator: (Connection) -> T) : DataSourceStub() {
        val connections = mutableListOf<T>()

        override fun getConnection(): Connection {
            val connection = DriverManager.getConnection(testDB.connection(), testDB.user, testDB.pass)
            val wrapped = connectionDecorator(connection)
            connections.add(wrapped)
            return wrapped
        }
    }

    private class RollbackException : SQLTransientException()
    private class ExceptionOnRollbackConnection(connection: Connection) : ConnectionSpy(connection) {
        override fun rollback() {
            super.rollback()
            throw RollbackException()
        }
    }

    @Test
    fun `transaction repetition works even if rollback throws exception`() {
        `_transaction repetition works even if rollback throws exception`(::ExceptionOnRollbackConnection)
    }

    private fun `_transaction repetition works even if rollback throws exception`(connectionDecorator: (Connection) -> ConnectionSpy) {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        Class.forName(TestDB.H2_V2.driver).getDeclaredConstructor().newInstance()

        val wrappingDataSource = WrappingDataSource(TestDB.H2_V2, connectionDecorator)
        val db = Database.connect(datasource = wrappingDataSource)
        try {
            transaction(Connection.TRANSACTION_SERIALIZABLE, db = db) {
                maxAttempts = 5
                this.exec("BROKEN_SQL_THAT_CAUSES_EXCEPTION()")
            }
            fail("Should have thrown an exception")
        } catch (e: SQLException) {
            assertContains(e.toString(), "BROKEN_SQL_THAT_CAUSES_EXCEPTION", ignoreCase = false)
            assertEquals(5, wrappingDataSource.connections.size)
            wrappingDataSource.connections.forEach {
                assertFalse(it.commitCalled)
                assertTrue(it.rollbackCalled)
                assertTrue(it.closeCalled)
            }
        }
    }

    private class CommitException : SQLTransientException()
    private class ExceptionOnCommitConnection(connection: Connection) : ConnectionSpy(connection) {
        override fun commit() {
            super.commit()
            throw CommitException()
        }
    }

    @Test
    fun `transaction repetition works when commit throws exception`() {
        `_transaction repetition works when commit throws exception`(::ExceptionOnCommitConnection)
    }

    private fun `_transaction repetition works when commit throws exception`(connectionDecorator: (Connection) -> ConnectionSpy) {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        Class.forName(TestDB.H2_V2.driver).getDeclaredConstructor().newInstance()

        val wrappingDataSource = WrappingDataSource(TestDB.H2_V2, connectionDecorator)
        val db = Database.connect(datasource = wrappingDataSource)
        try {
            transaction(Connection.TRANSACTION_SERIALIZABLE, db = db) {
                maxAttempts = 5
                this.exec("SELECT 1;")
            }
            fail("Should have thrown an exception")
        } catch (_: CommitException) {
            assertEquals(5, wrappingDataSource.connections.size)
            wrappingDataSource.connections.forEach {
                assertTrue(it.commitCalled)
                assertTrue(it.closeCalled)
            }
        }
    }

    @Test
    fun `transaction throws exception if all commits throws exception`() {
        `_transaction throws exception if all commits throws exception`(::ExceptionOnCommitConnection)
    }

    private fun `_transaction throws exception if all commits throws exception`(connectionDecorator: (Connection) -> ConnectionSpy) {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        Class.forName(TestDB.H2_V2.driver).getDeclaredConstructor().newInstance()

        val wrappingDataSource = WrappingDataSource(TestDB.H2_V2, connectionDecorator)
        val db = Database.connect(datasource = wrappingDataSource)
        try {
            transaction(Connection.TRANSACTION_SERIALIZABLE, db = db) {
                maxAttempts = 5
                this.exec("SELECT 1;")
            }
            fail("Should have thrown an exception")
        } catch (_: CommitException) {
            // Yay
        }
    }

    private class CloseException : SQLTransientException()
    private class ExceptionOnRollbackCloseConnection(connection: Connection) : ConnectionSpy(connection) {
        override fun rollback() {
            super.rollback()
            throw RollbackException()
        }

        override fun close() {
            super.close()
            throw CloseException()
        }
    }

    @Test
    fun `transaction repetition works even if rollback and close throws exception`() {
        `_transaction repetition works even if rollback throws exception`(::ExceptionOnRollbackCloseConnection)
    }

    @Test
    fun `transaction repetition works when commit and close throws exception`() {
        `_transaction repetition works when commit throws exception`(::ExceptionOnCommitConnection)
    }

    private class ExceptionOnCommitCloseConnection(connection: Connection) : ConnectionSpy(connection) {
        override fun commit() {
            super.commit()
            throw CommitException()
        }

        override fun close() {
            super.close()
            throw CloseException()
        }
    }

    @Test
    fun `transaction throws exception if all commits and close throws exception`() {
        `_transaction throws exception if all commits throws exception`(::ExceptionOnCommitCloseConnection)
    }

    @After
    fun teardown() {
        TransactionManager.resetCurrent(null)
    }
}
