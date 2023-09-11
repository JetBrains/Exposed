package org.jetbrains.exposed.spring

import java.io.PrintWriter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Savepoint
import java.util.logging.Logger
import javax.sql.DataSource

internal class DataSourceSpy(connectionSpy: (Connection) -> Connection) : DataSource {
    var con: Connection = connectionSpy(DriverManager.getConnection("jdbc:h2:mem:test"))

    override fun getConnection() = con
    override fun getLogWriter(): PrintWriter = throw NotImplementedError()
    override fun setLogWriter(out: PrintWriter?) = throw NotImplementedError()
    override fun setLoginTimeout(seconds: Int) = throw NotImplementedError()
    override fun getLoginTimeout(): Int = throw NotImplementedError()
    override fun getParentLogger(): Logger = throw NotImplementedError()
    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw NotImplementedError()
    override fun isWrapperFor(iface: Class<*>?): Boolean = throw NotImplementedError()
    override fun getConnection(username: String?, password: String?): Connection = throw NotImplementedError()
}

internal class ConnectionSpy(private val connection: Connection) : Connection by connection {

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

    override fun close() {
        callOrder.add("close")
        closeCallCount++
    }

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

    override fun setReadOnly(readOnly: Boolean) {
        callOrder.add("setReadOnly")
        mockReadOnly = readOnly
    }

    override fun isReadOnly(): Boolean {
        callOrder.add("isReadOnly")
        return mockReadOnly
    }

    override fun getTransactionIsolation() = mockTransactionIsolation
}
