package org.jetbrains.exposed.sql.tests.shared

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.sqlite.SQLiteDataSource
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

class ConnectionTimeoutTest : DatabaseTestsBase(){

    private class ExceptionOnGetConnectionDataSource : DataSourceStub() {
        var connectCount = 0

        override fun getConnection(): Connection {
            connectCount++
            throw GetConnectException()
        }
    }

    private class GetConnectException : SQLTransientException()

    @Test
    fun `connect fail causes repeated connect attempts`(){
        val datasource = ExceptionOnGetConnectionDataSource()
        val db = Database.connect(datasource = datasource)

        try {
            transaction(TransactionManager.manager.defaultIsolationLevel, 42, db) {
                exec("SELECT 1;")
                // NO OP
            }
            fail("Should have thrown ${GetConnectException::class.simpleName}")
        } catch (e : ExposedSQLException){
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
    private fun `_transaction repetition works even if rollback throws exception`(connectionDecorator: (Connection) -> ConnectionSpy){
        Class.forName(TestDB.H2.driver).newInstance()

        val wrappingDataSource = ConnectionExceptions.WrappingDataSource(TestDB.H2, connectionDecorator)
        val db = Database.connect(datasource = wrappingDataSource)
        try {
            transaction(TransactionManager.manager.defaultIsolationLevel, 5, db) {
                this.exec("BROKEN_SQL_THAT_CAUSES_EXCEPTION()")
            }
            fail("Should have thrown an exception")
        } catch (e : SQLException){
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
        Class.forName(TestDB.H2.driver).newInstance()

        val wrappingDataSource = WrappingDataSource(TestDB.H2, connectionDecorator)
        val db = Database.connect(datasource = wrappingDataSource)
        try {
            transaction(TransactionManager.manager.defaultIsolationLevel, 5, db) {
                this.exec("SELECT 1;")
            }
            fail("Should have thrown an exception")
        } catch (e: CommitException) {
            assertEquals(5, wrappingDataSource.connections.size)
            wrappingDataSource.connections.forEach {
                assertTrue(it.commitCalled)
                assertTrue(it.closeCalled)
            }
        }
    }

    @Test
    fun `transaction throws exception if all commits throws exception`(){
        `_transaction throws exception if all commits throws exception`(::ExceptionOnCommitConnection)
    }
    private fun `_transaction throws exception if all commits throws exception`(connectionDecorator: (Connection) -> ConnectionSpy){
        Class.forName(TestDB.H2.driver).newInstance()

        val wrappingDataSource = ConnectionExceptions.WrappingDataSource(TestDB.H2, connectionDecorator)
        val db = Database.connect(datasource = wrappingDataSource)
        try {
            transaction(TransactionManager.manager.defaultIsolationLevel, 5, db) {
                this.exec("SELECT 1;")
            }
            fail("Should have thrown an exception")
        } catch (e : CommitException){
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
    fun `transaction repetition works even if rollback and close throws exception`(){
        `_transaction repetition works even if rollback throws exception`(::ExceptionOnRollbackCloseConnection)
    }

    @Test
    fun `transaction repetition works when commit and close throws exception`(){
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
    fun `transaction throws exception if all commits and close throws exception`(){
        `_transaction throws exception if all commits throws exception`(::ExceptionOnCommitCloseConnection)
    }

    @After
    fun `teardown`(){
        TransactionManager.resetCurrent(null)
    }

}

class ThreadLocalManagerTest : DatabaseTestsBase() {
    @Test
    fun testReconnection() {
        if (TestDB.MYSQL !in TestDB.enabledInTests()) return

        var secondThreadTm: TransactionManager? = null
        TestDB.MYSQL.connect()
        transaction {
            val firstThreadTm = TransactionManager.manager
            SchemaUtils.create(DMLTestsData.Cities)
            thread {
                TestDB.MYSQL.connect()
                transaction {
                    DMLTestsData.Cities.selectAll().toList()
                    secondThreadTm = TransactionManager.manager
                    assertNotEquals(firstThreadTm, secondThreadTm)
                }
            }.join()
            assertEquals(firstThreadTm, TransactionManager.manager)
            SchemaUtils.drop(DMLTestsData.Cities)
        }
        assertEquals(secondThreadTm, TransactionManager.manager)
    }
}

/**
 *
 * Issue #91: TransactionManager.currentThreadManager not always reset with multiple Database.connect calls 
 * https://github.com/JetBrains/Exposed/issues/91
 * 
 * Demonstrates bug for following sequence:
 *
 * 1. Connect to Database
 * 2. Access Database.url
 * 3. Discard underlying database (e.g. delete file)
 * 4. Connect to Database (with new underlying file)
 * 5. Perform a transaction which fails
 *
 */
class MultipleDatabaseBugTest {

    @Rule @JvmField
    val folder = TemporaryFolder()

    var db: Database? = null

    @Before
    fun before() {
        val filename = folder.newFile("foo.db").absolutePath
        val ds = SQLiteDataSource()
        ds.url = "jdbc:sqlite:" + filename
        db = Database.connect(ds)

        // SQLite supports only TRANSACTION_SERIALIZABLE and TRANSACTION_READ_UNCOMMITTED
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    }

    @Test
    fun test1() {
        printStuff(1)
    }

    @Test
    fun test2() {
        printStuff(2)
        initDb()
    }

    private fun printStuff(test: Int) {
        // Accessing "db?.url" lazily initializes Database.url by calling Database.metadata
        // which first calls TransactionManager.currentOrNull which initializes the
        // TransactionManager.currentThreadManager ThreadLocal value which is never removed if
        // there is no subsequent transaction
        println("test$test url: ${db?.url}")
    }

    private fun initDb() {
        transaction {
            println("TransactionManager: ${TransactionManager.manager}")
            println("Transaction connection url: ${connection.metaData?.url}")
        }
    }
}