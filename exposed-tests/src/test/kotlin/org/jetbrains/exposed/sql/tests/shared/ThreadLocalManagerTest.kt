package org.jetbrains.exposed.sql.tests.shared

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.junit.After
import org.junit.Assume
import org.junit.Test
import java.io.PrintWriter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.SQLTransientException
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.test.*

private open class DataSourceStub : DataSource {
    override fun setLogWriter(out: PrintWriter?): Unit = throw NotImplementedError()
    override fun getParentLogger(): Logger { throw NotImplementedError() }
    override fun setLoginTimeout(seconds: Int) { throw NotImplementedError() }
    override fun isWrapperFor(iface: Class<*>?): Boolean { throw NotImplementedError() }
    override fun getLogWriter(): PrintWriter { throw NotImplementedError() }
    override fun <T : Any?> unwrap(iface: Class<T>?): T { throw NotImplementedError() }
    override fun getConnection(): Connection { throw NotImplementedError() }
    override fun getConnection(username: String?, password: String?): Connection { throw NotImplementedError() }
    override fun getLoginTimeout(): Int { throw NotImplementedError() }
}

class ConnectionTimeoutTest : DatabaseTestsBase() {

    private class ExceptionOnGetConnectionDataSource : DataSourceStub() {
        var connectCount = 0

        override fun getConnection(): Connection {
            connectCount++
            throw GetConnectException()
        }
    }

    private class GetConnectException : SQLTransientException()

    @Test
    fun `connect fail causes repeated connect attempts`() {
        val datasource = ExceptionOnGetConnectionDataSource()
        val db = Database.connect(datasource = datasource)

        try {
            transaction(Connection.TRANSACTION_SERIALIZABLE, 42, db = db) {
                exec("SELECT 1;")
                // NO OP
            }
            fail("Should have thrown ${GetConnectException::class.simpleName}")
        } catch (e: ExposedSQLException) {
            assertTrue(e.cause is GetConnectException)
            assertEquals(42, datasource.connectCount)
        }
    }
}

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
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledInTests())
        Class.forName(TestDB.H2.driver).newInstance()

        val wrappingDataSource = ConnectionExceptions.WrappingDataSource(TestDB.H2, connectionDecorator)
        val db = Database.connect(datasource = wrappingDataSource)
        try {
            transaction(Connection.TRANSACTION_SERIALIZABLE, 5, db = db) {
                this.exec("BROKEN_SQL_THAT_CAUSES_EXCEPTION()")
            }
            fail("Should have thrown an exception")
        } catch (e: SQLException) {
            assertThat(e.toString(), Matchers.containsString("BROKEN_SQL_THAT_CAUSES_EXCEPTION"))
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
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledInTests())
        Class.forName(TestDB.H2.driver).newInstance()

        val wrappingDataSource = WrappingDataSource(TestDB.H2, connectionDecorator)
        val db = Database.connect(datasource = wrappingDataSource)
        try {
            transaction(Connection.TRANSACTION_SERIALIZABLE, 5, db = db) {
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
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledInTests())
        Class.forName(TestDB.H2.driver).newInstance()

        val wrappingDataSource = ConnectionExceptions.WrappingDataSource(TestDB.H2, connectionDecorator)
        val db = Database.connect(datasource = wrappingDataSource)
        try {
            transaction(Connection.TRANSACTION_SERIALIZABLE, 5, db = db) {
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
    fun `teardown`() {
        TransactionManager.resetCurrent(null)
    }
}

class ThreadLocalManagerTest : DatabaseTestsBase() {
    @Test
    fun testReconnection() {
        Assume.assumeTrue(TestDB.MYSQL in TestDB.enabledInTests())

        var secondThreadTm: TransactionManager? = null
        val db1 = TestDB.MYSQL.connect()
        lateinit var db2: Database

        transaction {
            val firstThreadTm = db1.transactionManager
            SchemaUtils.create(DMLTestsData.Cities)
            thread {
                db2 = TestDB.MYSQL.connect()
                transaction {
                    DMLTestsData.Cities.selectAll().toList()
                    secondThreadTm = db2.transactionManager
                    assertNotEquals(firstThreadTm, secondThreadTm)
                }
            }.join()
            assertEquals(firstThreadTm, db1.transactionManager)
            SchemaUtils.drop(DMLTestsData.Cities)
        }
        assertEquals(secondThreadTm, db2.transactionManager)
    }

    @Test
    fun testReadOnly() {
        //Explanation: MariaDB driver never set readonly to true, MSSQL silently ignores the call, SQLLite does not
        // promise anything, H2 has very limited functionality
        withTables(excludeSettings = listOf(TestDB.SQLITE, TestDB.H2,
                                            TestDB.H2_MYSQL, TestDB.MARIADB,
                                            TestDB.SQLSERVER, TestDB.ORACLE), RollbackTable) {
            assertFails {
                inTopLevelTransaction(db.transactionManager.defaultIsolationLevel, 1, true) {
                    RollbackTable.insert { it[value] = "random-something" }
                }
            }.message?.run { assertTrue(contains("read-only")) } ?: fail("message should not be null")
        }
    }
}

object RollbackTable : IntIdTable() {
    val value = varchar("value", 20)
}

class RollbackTransactionTest : DatabaseTestsBase() {

    @Test
    fun testRollbackWithoutSavepoints() {
        withTables(RollbackTable) {
            inTopLevelTransaction(db.transactionManager.defaultIsolationLevel, 1) {
                RollbackTable.insert { it[value] = "before-dummy" }
                transaction {
                    assertEquals(1L, RollbackTable.select { RollbackTable.value eq "before-dummy" }.count())
                    RollbackTable.insert { it[value] = "inner-dummy" }
                }
                assertEquals(1L, RollbackTable.select { RollbackTable.value eq "before-dummy" }.count())
                assertEquals(1L, RollbackTable.select { RollbackTable.value eq "inner-dummy" }.count())
                RollbackTable.insert { it[value] = "after-dummy" }
                assertEquals(1L, RollbackTable.select { RollbackTable.value eq "after-dummy" }.count())
                rollback()
            }
            assertEquals(0L, RollbackTable.select { RollbackTable.value eq "before-dummy" }.count())
            assertEquals(0L, RollbackTable.select { RollbackTable.value eq "inner-dummy" }.count())
            assertEquals(0L, RollbackTable.select { RollbackTable.value eq "after-dummy" }.count())
        }
    }

    @Test
    fun testRollbackWithSavepoints() {
        withTables(RollbackTable) {
            try {
                db.useNestedTransactions = true
                inTopLevelTransaction(db.transactionManager.defaultIsolationLevel, 1) {
                    RollbackTable.insert { it[value] = "before-dummy" }
                    transaction {
                        assertEquals(1L, RollbackTable.select { RollbackTable.value eq "before-dummy" }.count())
                        RollbackTable.insert { it[value] = "inner-dummy" }
                        rollback()
                    }
                    assertEquals(1L, RollbackTable.select { RollbackTable.value eq "before-dummy" }.count())
                    assertEquals(0L, RollbackTable.select { RollbackTable.value eq "inner-dummy" }.count())
                    RollbackTable.insert { it[value] = "after-dummy" }
                    assertEquals(1L, RollbackTable.select { RollbackTable.value eq "after-dummy" }.count())
                    rollback()
                }
                assertEquals(0L, RollbackTable.select { RollbackTable.value eq "before-dummy" }.count())
                assertEquals(0L, RollbackTable.select { RollbackTable.value eq "inner-dummy" }.count())
                assertEquals(0L, RollbackTable.select { RollbackTable.value eq "after-dummy" }.count())
            } finally {
                db.useNestedTransactions = false
            }
        }
    }
}

class TransactionIsolationTest : DatabaseTestsBase() {
    @Test
    fun `test what transaction isolation was applied`() {
        withDb {
            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, this.connection.transactionIsolation)
            }
        }
    }
}

class TransactionManagerResetTest {
    @Test
    fun `test closeAndUnregister with next Database-connect works fine`() {
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledInTests())
        val initialManager = TransactionManager.manager
        val db1 = TestDB.H2.connect()
        val db1TransactionManager = TransactionManager.managerFor(db1)
        assertEquals(initialManager, TransactionManager.manager)
        transaction(db1) {
            assertEquals(db1TransactionManager, TransactionManager.manager)
            exec("SELECT 1 from dual;")
        }
        TransactionManager.closeAndUnregister(db1)
        assertEquals(initialManager, TransactionManager.manager)
        val db2 = TestDB.H2.connect()
        // Check should be made in a separate thread as in current thread manager is already initialized
        thread {
            assertEquals(TransactionManager.managerFor(db2), TransactionManager.manager)
        }.join()
    }
}
