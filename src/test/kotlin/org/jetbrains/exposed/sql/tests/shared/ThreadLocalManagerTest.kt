package org.jetbrains.exposed.sql.tests.shared

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
import java.sql.SQLTransientException
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

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
            connectCount++;
            throw GetConnectException()
        }
    }

    private class GetConnectException : SQLTransientException()

    @Test
    fun `connect fail causes repeated connect attempts`(){
        val datasource = ExceptionOnGetConnectionDataSource()
        Database.connect(datasource = datasource)

        try {
            transaction(TransactionManager.manager.defaultIsolationLevel, 42) {
                // NO OP
            }
            fail("Should have thrown ${GetConnectException::class.simpleName}")
        } catch (e : GetConnectException){
            assertEquals(42, datasource.connectCount)
        }
    }

    @After
    fun `teardown`(){
        TransactionManager.removeCurrent()
    }

}

class ThreadLocalManagerTest : DatabaseTestsBase() {
    @Test
    fun testReconnection() {
        var secondThreadTm: TransactionManager? = null
        var isMysql = false
        withDb(TestDB.MYSQL) {
            isMysql = true
            SchemaUtils.create(DMLTestsData.Cities)
            val firstThreadTm = TransactionManager.manager
            thread {
                withDb(TestDB.MYSQL) {
                    DMLTestsData.Cities.selectAll().toList()
                    secondThreadTm = TransactionManager.manager
                    assertNotEquals(firstThreadTm, secondThreadTm)
                }
            }.join()
            assertEquals(firstThreadTm, TransactionManager.manager)
            SchemaUtils.drop(DMLTestsData.Cities)
        }
        if (isMysql) {
            assertEquals(secondThreadTm, TransactionManager.manager)
        }
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