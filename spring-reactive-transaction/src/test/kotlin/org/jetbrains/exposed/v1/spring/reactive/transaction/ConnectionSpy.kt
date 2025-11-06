package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.Connection
import io.r2dbc.spi.IsolationLevel
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

internal class ConnectionSpy(private val connection: Connection) : Connection by connection {
    var commitCallCount: Int = 0
    var rollbackCallCount: Int = 0
    var closeCallCount: Int = 0
    var releaseSavepointCallCount: Int = 0
    var mockAutoCommit: Boolean = false
    var mockTransactionIsolation: IsolationLevel = IsolationLevel.READ_COMMITTED
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
        releaseSavepointCallCount = 0
        mockAutoCommit = false
        mockTransactionIsolation = IsolationLevel.READ_COMMITTED
        mockCommit = {}
        mockRollback = {}
        callOrder.clear()
    }

    override fun close(): Publisher<Void?> {
        callOrder.add("close")
        closeCallCount++

        return Mono.empty()
    }

    override fun setAutoCommit(p0: Boolean): Publisher<Void?> {
        callOrder.add("setAutoCommit")
        mockAutoCommit = p0

        return Mono.empty()
    }

    override fun isAutoCommit(): Boolean = mockAutoCommit

    override fun commitTransaction(): Publisher<Void?> {
        callOrder.add("commit")
        commitCallCount++
        mockCommit()

        return Mono.empty()
    }

    override fun rollbackTransaction(): Publisher<Void?> {
        callOrder.add("rollback")
        rollbackCallCount++
        mockRollback()

        return Mono.empty()
    }

    override fun rollbackTransactionToSavepoint(p0: String): Publisher<Void?> {
        callOrder.add("rollback")
        rollbackCallCount++
        mockRollback()

        return Mono.empty()
    }

    override fun releaseSavepoint(p0: String): Publisher<Void?> {
        callOrder.add("releaseSavepoint")
        releaseSavepointCallCount++

        return Mono.empty()
    }

    override fun getTransactionIsolationLevel(): IsolationLevel = mockTransactionIsolation
}
