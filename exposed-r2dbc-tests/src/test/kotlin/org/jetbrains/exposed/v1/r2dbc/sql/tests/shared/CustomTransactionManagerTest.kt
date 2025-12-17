package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcExposedConnection
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.R2dbcTransactionInterface
import org.jetbrains.exposed.v1.r2dbc.transactions.R2dbcTransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CustomTransactionManagerTest : R2dbcDatabaseTestsBase() {

    private class MockTransactionManager(
        override val db: R2dbcDatabase,
        private val mockTransaction: R2dbcTransaction
    ) : R2dbcTransactionManager {
        var newTransactionCalls = 0
            private set

        override var defaultReadOnly: Boolean = false
        override var defaultMaxAttempts: Int = 5
        override var defaultMinRetryDelay: Long = 50L
        override var defaultMaxRetryDelay: Long = 500L
        override var defaultIsolationLevel: IsolationLevel? = IsolationLevel.READ_COMMITTED

        override fun newTransaction(
            isolation: IsolationLevel?,
            readOnly: Boolean?,
            outerTransaction: R2dbcTransaction?
        ): R2dbcTransaction {
            newTransactionCalls++
            return mockTransaction
        }
    }

    private fun createMockDatabase(): R2dbcDatabase {
        return R2dbcDatabase.connect(
            url = "r2dbc:h2:mem:///test_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1"
        )
    }

    private fun createMockTransaction(
        mockDb: R2dbcDatabase,
        managerProvider: () -> R2dbcTransactionManager,
        connectionError: String? = null,
        onCommit: suspend () -> Unit = {},
        onRollback: suspend () -> Unit = {},
        onClose: suspend () -> Unit = {}
    ): R2dbcTransaction {
        val mockTransactionImpl = object : R2dbcTransactionInterface {
            override val db = mockDb
            override suspend fun connection(): R2dbcExposedConnection<*> {
                throw IllegalStateException(connectionError ?: "Mock connection")
            }

            override val outerTransaction: R2dbcTransaction? = null
            override val readOnly: Boolean = false
            override val transactionIsolation: IsolationLevel? = IsolationLevel.READ_COMMITTED
            override val transactionManager: TransactionManagerApi get() = managerProvider()

            override suspend fun commit() = onCommit()
            override suspend fun rollback() = onRollback()
            override suspend fun close() = onClose()
        }

        return object : R2dbcTransaction(mockTransactionImpl) {}
    }

    @BeforeTest
    fun before() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
    }

    @Test
    fun testCustomTransactionManagerRegistrationWithoutRealDatabase() = runTest {
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
            assertEquals(IsolationLevel.READ_COMMITTED, registeredManager.defaultIsolationLevel)

            registeredManager.defaultReadOnly = true
            assertEquals(true, registeredManager.defaultReadOnly)

            registeredManager.defaultMaxAttempts = 10
            assertEquals(10, registeredManager.defaultMaxAttempts)
        } finally {
            TransactionManager.closeAndUnregister(mockDb)
        }
    }

    @Test
    fun testCustomTransactionManagerWithQueryExecution() = runTest {
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
                suspendTransaction(mockDb) {
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
    fun testCustomTransactionManagerWithSuccessfulCommit() = runTest {
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

            suspendTransaction(mockDb) {
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

    @Test
    fun testContextKeyCleanupAfterClosingConnection() = runTest {
        val mockDb = createMockDatabase()
        lateinit var customManager: MockTransactionManager
        val mockTransaction = createMockTransaction(mockDb, { customManager })

        customManager = MockTransactionManager(mockDb, mockTransaction)

        TransactionManager.registerManager(mockDb, customManager)

        @OptIn(InternalApi::class)
        val keyAfterRegistration = TransactionManager.getContextKey(customManager)
        assertNotNull(keyAfterRegistration)

        TransactionManager.closeAndUnregister(mockDb)

        @OptIn(InternalApi::class)
        assertFailsWith<IllegalStateException> {
            TransactionManager.getContextKey(customManager)
        }
    }
}
