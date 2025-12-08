package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextHolder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection
import org.jetbrains.exposed.v1.jdbc.transactions.JdbcTransactionInterface
import org.jetbrains.exposed.v1.jdbc.transactions.JdbcTransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Assumptions
import java.sql.Connection.TRANSACTION_READ_COMMITTED
import kotlin.coroutines.CoroutineContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CustomTransactionManagerTest : DatabaseTestsBase() {

    private class MockTransactionManager(
        override val db: Database,
        private val mockTransaction: JdbcTransaction
    ) : JdbcTransactionManager {
        var newTransactionCalls = 0
            private set

        override var defaultReadOnly: Boolean = false
        override var defaultMaxAttempts: Int = 5
        override var defaultMinRetryDelay: Long = 50L
        override var defaultMaxRetryDelay: Long = 500L
        override var defaultIsolationLevel: Int = TRANSACTION_READ_COMMITTED

        override fun newTransaction(
            isolation: Int,
            readOnly: Boolean,
            outerTransaction: JdbcTransaction?
        ): JdbcTransaction {
            newTransactionCalls++
            return mockTransaction
        }

        override val contextKey = object : CoroutineContext.Key<TransactionContextHolder> {}
    }

    private fun createMockDatabase(): Database {
        return Database.connect(
            getNewConnection = { throw UnsupportedOperationException("Mock: no real connection") }
        )
    }

    private fun createMockTransaction(
        mockDb: Database,
        managerProvider: () -> JdbcTransactionManager,
        connectionError: String? = null,
        onCommit: () -> Unit = {},
        onRollback: () -> Unit = {},
        onClose: () -> Unit = {}
    ): JdbcTransaction {
        val mockTransactionImpl = object : JdbcTransactionInterface {
            override val db = mockDb
            override val connection: ExposedConnection<*>
                get() = throw IllegalStateException(connectionError ?: "Mock connection")
            override val outerTransaction: JdbcTransaction? = null
            override val readOnly: Boolean = false
            override val transactionIsolation: Int = TRANSACTION_READ_COMMITTED
            override val transactionManager: TransactionManagerApi get() = managerProvider()

            override fun commit() = onCommit()
            override fun rollback() = onRollback()
            override fun close() = onClose()
        }

        return object : JdbcTransaction(mockTransactionImpl) {}
    }

    @BeforeTest
    fun before() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
    }

    @Test
    fun testCustomTransactionManagerRegistrationWithoutRealDatabase() {
        val mockDb = createMockDatabase()
        lateinit var customManager: MockTransactionManager
        val mockTransaction = createMockTransaction(mockDb, { customManager })

        customManager = MockTransactionManager(mockDb, mockTransaction)

        try {
            TransactionManager.registerManager(mockDb, customManager)

            val registeredManager = TransactionManager.managerFor(mockDb)
            assertEquals(customManager, registeredManager)

            val newTx = registeredManager.newTransaction(
                registeredManager.defaultIsolationLevel,
                registeredManager.defaultReadOnly,
                null
            )
            assertNotNull(newTx)
            assertEquals(mockTransaction, newTx)
            assertEquals(1, customManager.newTransactionCalls)

            assertEquals(false, registeredManager.defaultReadOnly)
            assertEquals(5, registeredManager.defaultMaxAttempts)
            assertEquals(50L, registeredManager.defaultMinRetryDelay)
            assertEquals(500L, registeredManager.defaultMaxRetryDelay)
            assertEquals(TRANSACTION_READ_COMMITTED, registeredManager.defaultIsolationLevel)

            registeredManager.defaultReadOnly = true
            assertEquals(true, registeredManager.defaultReadOnly)

            registeredManager.defaultMaxAttempts = 10
            assertEquals(10, registeredManager.defaultMaxAttempts)
        } finally {
            TransactionManager.closeAndUnregister(mockDb)
        }
    }

    @Test
    fun testCustomTransactionManagerWithQueryExecution() {
        var commitCalls = 0
        var rollbackCalls = 0
        var closeCalls = 0

        val mockDb = createMockDatabase()
        lateinit var customManager: MockTransactionManager
        val mockTransaction = createMockTransaction(
            mockDb = mockDb,
            managerProvider = { customManager },
            connectionError = "Mock connection access - transaction manager is being used!",
            onCommit = { commitCalls++ },
            onRollback = { rollbackCalls++ },
            onClose = { closeCalls++ }
        )

        customManager = MockTransactionManager(mockDb, mockTransaction).apply {
            defaultMaxAttempts = 1
            defaultMinRetryDelay = 0L
            defaultMaxRetryDelay = 0L
        }

        try {
            TransactionManager.registerManager(mockDb, customManager)

            val exception = assertFailsWith<IllegalStateException> {
                transaction(mockDb) {
                    exec("SELECT 1") { }
                }
            }

            assertEquals("Mock connection access - transaction manager is being used!", exception.message)
            assertEquals(1, customManager.newTransactionCalls)
            assertEquals(1, rollbackCalls)
            assertEquals(1, closeCalls)
            assertEquals(0, commitCalls)
        } finally {
            TransactionManager.closeAndUnregister(mockDb)
        }
    }

    @Test
    fun testCustomTransactionManagerWithSuccessfulCommit() {
        var commitCalls = 0
        var rollbackCalls = 0
        var closeCalls = 0

        val mockDb = createMockDatabase()
        lateinit var customManager: MockTransactionManager
        val mockTransaction = createMockTransaction(
            mockDb = mockDb,
            managerProvider = { customManager },
            onCommit = { commitCalls++ },
            onRollback = { rollbackCalls++ },
            onClose = { closeCalls++ }
        )

        customManager = MockTransactionManager(mockDb, mockTransaction)

        try {
            TransactionManager.registerManager(mockDb, customManager)

            transaction(mockDb) {
                // Empty transaction block - no database operations
            }

            assertEquals(1, customManager.newTransactionCalls)
            assertEquals(1, commitCalls)
            assertEquals(1, closeCalls)
            assertEquals(0, rollbackCalls)
        } finally {
            TransactionManager.closeAndUnregister(mockDb)
        }
    }
}
