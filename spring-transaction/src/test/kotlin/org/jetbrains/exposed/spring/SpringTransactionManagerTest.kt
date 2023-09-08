package org.jetbrains.exposed.spring

import junit.framework.TestCase.assertEquals
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.TransactionTemplate
import java.io.PrintWriter
import java.sql.*
import java.util.*
import java.util.concurrent.Executor
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpringTransactionManagerTest {

    private val ds1 = DataSourceSpy()
    private val con1 = ConnectionSpy()
    private val ds2 = DataSourceSpy()
    private val con2 = ConnectionSpy()

    @BeforeTest
    fun beforeTest() {
        ds1.clearMock()
        ds2.clearMock()
        ds1.mockConnection(con1)
        ds2.mockConnection(con2)
        con1.clearMock()
        con2.clearMock()
    }

    @AfterTest
    fun afterTest() {
        while (TransactionManager.defaultDatabase == null) {
            TransactionManager.defaultDatabase?.let { TransactionManager.closeAndUnregister(it) }
        }
    }

    @Test
    fun `set manager when transaction start`() {
        val tm = SpringTransactionManager(ds1)
        tm.executeAssert(false)
    }

    @Test
    fun `set right transaction manager when two transaction manager exist`() {
        val tm = SpringTransactionManager(ds1)
        tm.executeAssert(false)

        val tm2 = SpringTransactionManager(ds2)
        tm2.executeAssert(false)
    }

    @Test
    fun `set right transaction manager when two transaction manager with nested transaction template`() {
        val tm = SpringTransactionManager(ds1)
        val tm2 = SpringTransactionManager(ds2)

        tm2.executeAssert(false) {
            tm.executeAssert(false)
            assertEquals(
                TransactionManager.managerFor(TransactionManager.currentOrNull()?.db),
                TransactionManager.manager
            )
        }
    }

    @Test
    fun `connection commit and close when transaction success`() {
        val tm = SpringTransactionManager(ds1)
        tm.executeAssert()

        assertTrue(con1.verifyCallOrder("setReadOnly", "setAutoCommit", "commit", "close"))
        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `connection rollback and close when transaction fail`() {
        val tm = SpringTransactionManager(ds1)
        val ex = RuntimeException("Application exception")
        try {
            tm.executeAssert {
                throw ex
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `connection commit and closed when nested transaction success`() {
        val tm = SpringTransactionManager(ds1)
        tm.executeAssert {
            tm.executeAssert()
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `connection commit and closed when two different transaction manager with nested transaction success`() {
        val tm1 = SpringTransactionManager(ds1)
        val tm2 = SpringTransactionManager(ds2)

        tm1.executeAssert {
            tm2.executeAssert()
            assertEquals(
                TransactionManager.managerFor(TransactionManager.currentOrNull()?.db),
                TransactionManager.manager
            )
        }

        assertEquals(1, con2.commitCallCount)
        assertEquals(1, con2.closeCallCount)
        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `connection rollback and closed when two different transaction manager with nested transaction failed`() {
        val tm1 = SpringTransactionManager(ds1)
        val tm2 = SpringTransactionManager(ds2)
        val ex = RuntimeException("Application exception")
        try {
            tm1.executeAssert {
                tm2.executeAssert {
                    throw ex
                }
                assertEquals(
                    TransactionManager.managerFor(TransactionManager.currentOrNull()?.db),
                    TransactionManager.manager
                )
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }

        assertEquals(0, con2.commitCallCount)
        assertEquals(1, con2.rollbackCallCount)
        assertEquals(1, con2.closeCallCount)
        assertEquals(0, con1.commitCallCount)
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `transaction commit with lazy connection data source proxy`() {
        val lazyDs = LazyConnectionDataSourceProxy(ds1)
        val tm = SpringTransactionManager(lazyDs)
        tm.executeAssert()

        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `transaction rollback with lazy connection data source proxy`() {
        val lazyDs = LazyConnectionDataSourceProxy(ds1)
        val tm = SpringTransactionManager(lazyDs)
        val ex = RuntimeException("Application exception")
        try {
            tm.executeAssert {
                throw ex
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `transaction commit with transaction aware data source proxy`() {
        val transactionAwareDs = TransactionAwareDataSourceProxy(ds1)
        val tm = SpringTransactionManager(transactionAwareDs)
        tm.executeAssert()

        assertTrue(con1.verifyCallOrder("setReadOnly", "setAutoCommit", "commit"))
        assertEquals(1, con1.commitCallCount)
        assertTrue(con1.closeCallCount > 0)
    }

    @Test
    fun `transaction rollback with transaction aware data source proxy`() {
        val transactionAwareDs = TransactionAwareDataSourceProxy(ds1)
        val tm = SpringTransactionManager(transactionAwareDs)
        val ex = RuntimeException("Application exception")
        try {
            tm.executeAssert {
                throw ex
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }

        assertTrue(con1.verifyCallOrder("setReadOnly", "setAutoCommit", "rollback"))
        assertEquals(1, con1.rollbackCallCount)
        assertTrue(con1.closeCallCount > 0)
    }

    @Test
    fun `transaction exception on commit and rollback on commit failure`() {
        con1.mockCommit = { throw SQLException("Commit failure") }

        val tm = SpringTransactionManager(ds1)
        tm.isRollbackOnCommitFailure = true
        assertFailsWith<TransactionSystemException> {
            tm.executeAssert()
        }

        assertTrue(con1.verifyCallOrder("setReadOnly", "setAutoCommit", "commit", "isClosed", "rollback", "close"))
        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `transaction with exception on rollback`() {
        con1.mockRollback = { throw SQLException("Rollback failure") }

        val tm = SpringTransactionManager(ds1)
        assertFailsWith<TransactionSystemException> {
            tm.executeAssert {
                assertEquals(false, it.isRollbackOnly)
                it.setRollbackOnly()
                assertEquals(true, it.isRollbackOnly)
            }
        }

        assertTrue(con1.verifyCallOrder("setReadOnly", "setAutoCommit", "isClosed", "rollback", "close"))
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    private fun SpringTransactionManager.executeAssert(
        initializeConnection: Boolean = true,
        body: (TransactionStatus) -> Unit = {}
    ) {
        TransactionTemplate(this).executeWithoutResult {
            assertEquals(
                TransactionManager.managerFor(TransactionManager.currentOrNull()?.db),
                TransactionManager.manager
            )
            if (initializeConnection) TransactionManager.current().connection
            body(it)
        }
    }

    class DataSourceSpy : DataSource {

        private var connection: Connection? = null
            set(value) {
                field = value
            }

        fun clearMock() {
            connection = null
        }

        fun mockConnection(connection: Connection) {
            this.connection = connection
        }

        override fun getLogWriter(): PrintWriter {
            throw NotImplementedError()
        }

        override fun setLogWriter(out: PrintWriter?) {
            throw NotImplementedError()
        }

        override fun setLoginTimeout(seconds: Int) {
            throw NotImplementedError()
        }

        override fun getLoginTimeout(): Int {
            throw NotImplementedError()
        }

        override fun getParentLogger(): Logger {
            throw NotImplementedError()
        }

        override fun <T : Any?> unwrap(iface: Class<T>?): T {
            throw NotImplementedError()
        }

        override fun isWrapperFor(iface: Class<*>?): Boolean {
            throw NotImplementedError()
        }

        override fun getConnection() = if (connection != null) connection!! else throw NotImplementedError()
        override fun getConnection(username: String?, password: String?): Connection {
            throw NotImplementedError()
        }
    }

    class ConnectionSpy : Connection {

        var commitCallCount: Int = 0
        var rollbackCallCount: Int = 0
        var closeCallCount: Int = 0
        var mockReadOnly: Boolean = false
        var mockIsClosed: Boolean = false
        var mockAutoCommit: Boolean = false
        var mockTransactionIsolation: Int = Connection.TRANSACTION_READ_COMMITTED
        var mockCommit: () -> Unit = {}
        var mockRollback: () -> Unit = {}
        private val callOrder = mutableListOf<String>()

        fun verifyCallOrder(vararg functions: String): Boolean {
            val indices = functions.map { callOrder.indexOf(it) }
            return indices.none { it == -1 } && indices == indices.sorted()
        }

        fun clearMock() {
            commitCallCount = 0
            rollbackCallCount = 0
            closeCallCount = 0
            mockAutoCommit = false
            mockReadOnly = false
            mockIsClosed = false
            mockTransactionIsolation = Connection.TRANSACTION_READ_COMMITTED
            mockCommit = {}
            mockRollback = {}
            callOrder.clear()
        }

        override fun <T : Any?> unwrap(iface: Class<T>?): T = throw NotImplementedError()
        override fun isWrapperFor(iface: Class<*>?): Boolean = throw NotImplementedError()
        override fun close() {
            callOrder.add("close")
            closeCallCount++
        }

        override fun createStatement(): Statement = throw NotImplementedError()
        override fun createStatement(
            resultSetType: Int,
            resultSetConcurrency: Int
        ): Statement = throw NotImplementedError()

        override fun createStatement(
            resultSetType: Int,
            resultSetConcurrency: Int,
            resultSetHoldability: Int
        ): Statement = throw NotImplementedError()

        override fun prepareStatement(sql: String?): PreparedStatement = throw NotImplementedError()
        override fun prepareStatement(
            sql: String?,
            resultSetType: Int,
            resultSetConcurrency: Int
        ): PreparedStatement = throw NotImplementedError()

        override fun prepareStatement(
            sql: String?,
            resultSetType: Int,
            resultSetConcurrency: Int,
            resultSetHoldability: Int
        ): PreparedStatement = throw NotImplementedError()

        override fun prepareStatement(
            sql: String?,
            autoGeneratedKeys: Int
        ): PreparedStatement = throw NotImplementedError()

        override fun prepareStatement(
            sql: String?,
            columnIndexes: IntArray?
        ): PreparedStatement = throw NotImplementedError()

        override fun prepareStatement(sql: String?, columnNames: Array<out String>?): PreparedStatement = throw NotImplementedError()
        override fun prepareCall(sql: String?): CallableStatement = throw NotImplementedError()
        override fun prepareCall(
            sql: String?,
            resultSetType: Int,
            resultSetConcurrency: Int
        ): CallableStatement = throw NotImplementedError()

        override fun prepareCall(
            sql: String?,
            resultSetType: Int,
            resultSetConcurrency: Int,
            resultSetHoldability: Int
        ): CallableStatement = throw NotImplementedError()

        override fun nativeSQL(sql: String?): String = throw NotImplementedError()
        override fun setAutoCommit(autoCommit: Boolean) {
            callOrder.add("setAutoCommit")
            mockAutoCommit = autoCommit
        }

        override fun getAutoCommit() = mockAutoCommit
        override fun commit() {
            callOrder.add("commit")
            commitCallCount++
            mockCommit()
        }

        override fun rollback() {
            callOrder.add("rollback")
            rollbackCallCount++
            mockRollback()
        }

        override fun rollback(savepoint: Savepoint?) = Unit
        override fun isClosed(): Boolean {
            callOrder.add("isClosed")
            return mockIsClosed
        }

        override fun getMetaData(): DatabaseMetaData = throw NotImplementedError()
        override fun setReadOnly(readOnly: Boolean) {
            callOrder.add("setReadOnly")
            mockReadOnly = readOnly
        }

        override fun isReadOnly(): Boolean {
            callOrder.add("isReadOnly")
            return mockReadOnly
        }

        override fun setCatalog(catalog: String?) = throw NotImplementedError()
        override fun getCatalog(): String = throw NotImplementedError()
        override fun setTransactionIsolation(level: Int) = throw NotImplementedError()
        override fun getTransactionIsolation() = mockTransactionIsolation
        override fun getWarnings(): SQLWarning = throw NotImplementedError()
        override fun clearWarnings() = throw NotImplementedError()
        override fun getTypeMap(): MutableMap<String, Class<*>> = throw NotImplementedError()
        override fun setTypeMap(map: MutableMap<String, Class<*>>?) = throw NotImplementedError()
        override fun setHoldability(holdability: Int) = throw NotImplementedError()
        override fun getHoldability(): Int = throw NotImplementedError()
        override fun setSavepoint(): Savepoint = throw NotImplementedError()
        override fun setSavepoint(name: String?): Savepoint = throw NotImplementedError()
        override fun releaseSavepoint(savepoint: Savepoint?) = throw NotImplementedError()
        override fun createClob(): Clob = throw NotImplementedError()
        override fun createBlob(): Blob = throw NotImplementedError()
        override fun createNClob(): NClob = throw NotImplementedError()
        override fun createSQLXML(): SQLXML = throw NotImplementedError()
        override fun isValid(timeout: Int): Boolean = throw NotImplementedError()
        override fun setClientInfo(name: String?, value: String?) = throw NotImplementedError()
        override fun setClientInfo(properties: Properties?) = throw NotImplementedError()
        override fun getClientInfo(name: String?): String = throw NotImplementedError()
        override fun getClientInfo(): Properties = throw NotImplementedError()
        override fun createArrayOf(
            typeName: String?,
            elements: Array<out Any>?
        ): java.sql.Array = throw NotImplementedError()

        override fun createStruct(typeName: String?, attributes: Array<out Any>?): Struct = throw NotImplementedError()
        override fun setSchema(schema: String?) = throw NotImplementedError()
        override fun getSchema(): String = throw NotImplementedError()
        override fun abort(executor: Executor?) = throw NotImplementedError()
        override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) = throw NotImplementedError()
        override fun getNetworkTimeout(): Int = throw NotImplementedError()
    }
}
