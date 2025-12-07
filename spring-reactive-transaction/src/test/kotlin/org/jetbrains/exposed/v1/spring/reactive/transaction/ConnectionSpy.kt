package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.Connection
import io.r2dbc.spi.IsolationLevel
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

class ConnectionSpy(private val connection: Connection) : Connection by connection {
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

    override fun close(): Publisher<Void?> = Mono.defer {
        callOrder.add("close")
        closeCallCount++

        Mono.empty<Void>()
    } as Publisher<Void?>

    override fun setAutoCommit(p0: Boolean): Publisher<Void?> = Mono.defer {
        callOrder.add("setAutoCommit")
        mockAutoCommit = p0

        Mono.empty<Void>()
    } as Publisher<Void?>

    override fun isAutoCommit(): Boolean = mockAutoCommit

    override fun commitTransaction(): Publisher<Void?> = Mono.defer {
        callOrder.add("commit")
        commitCallCount++
        mockCommit()

        Mono.empty<Void>()
    } as Publisher<Void?>

    override fun rollbackTransaction(): Publisher<Void?> = Mono.defer {
        callOrder.add("rollback")
        rollbackCallCount++
        mockRollback()

        Mono.empty<Void>()
    } as Publisher<Void?>

    override fun rollbackTransactionToSavepoint(p0: String): Publisher<Void?> = Mono.defer {
        callOrder.add("rollback")
        rollbackCallCount++
        mockRollback()

        Mono.empty<Void>()
    } as Publisher<Void?>

    override fun releaseSavepoint(p0: String): Publisher<Void?> = Mono.defer {
        callOrder.add("releaseSavepoint")
        releaseSavepointCallCount++

        Mono.empty<Void>()
    } as Publisher<Void?>

    override fun getTransactionIsolationLevel(): IsolationLevel = mockTransactionIsolation
}
