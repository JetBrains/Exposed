package org.jetbrains.exposed.v1.spring7.reactive.transaction

import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.R2dbcRollbackException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.UncategorizedR2dbcException
import org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import org.springframework.transaction.support.DefaultTransactionDefinition
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpringReactiveTransactionManagerTest {

    companion object {
        val cf1 = ConnectionFactorySpy(::ConnectionSpy)
        lateinit var con1: ConnectionSpy
        val cf2 = ConnectionFactorySpy(::ConnectionSpy)
        lateinit var con2: ConnectionSpy

        var managers = mutableListOf<SpringReactiveTransactionManager>()

        @BeforeAll
        @JvmStatic
        fun init() = runTest {
            con1 = cf1.getCon() as ConnectionSpy
            con2 = cf2.getCon() as ConnectionSpy
        }
    }

    @BeforeEach
    fun beforeTest() {
        con1.clearMock()
        con2.clearMock()
    }

    @BeforeEach
    fun afterTest() {
        managers.forEach { it.close() }
        managers.clear()
    }

    @Test
    suspend fun `set manager when transaction start`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert(false)
    }

    @Test
    suspend fun `set right transaction manager when two transaction manager exist`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert(false)

        val tm2 = newTransactionManager(cf2)
        tm2.executeAssert(false)
    }

    @Test
    suspend fun `set right transaction manager when two transaction manager with nested transaction template`() {
        val tm = newTransactionManager(cf1)
        val tm2 = newTransactionManager(cf2)

        tm2.executeAssert(false) {
            tm.executeAssert(false)

            assertEquals(
                TransactionManager.currentOrNull()?.db?.let { TransactionManager.managerFor(it) },
                TransactionManager.current().transactionManager
            )
        }
    }

    @Test
    suspend fun `connection commit and close when transaction success`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert()

        assertTrue(con1.verifyCallOrder("setAutoCommit", "commit", "close"))
        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `connection rollback and close when transaction fail`() {
        val tm = newTransactionManager(cf1)
        val ex = RuntimeException("Application exception")
        try {
            tm.executeAssert {
                throw ex
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                currentCoroutineContext().ensureActive()
            }
            assertEquals(e::class.java, ex::class.java)
            assertEquals(e.message, ex.message)
        }
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `connection commit and closed when nested transaction success`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert {
            tm.executeAssert()
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `connection commit and closed when two different transaction manager with nested transaction success`() {
        val tm1 = newTransactionManager(cf1)
        val tm2 = newTransactionManager(cf2)

        tm1.executeAssert {
            tm2.executeAssert()
            assertEquals(
                TransactionManager.currentOrNull()?.db?.let { TransactionManager.managerFor(it) },
                TransactionManager.current().transactionManager
            )
        }

        assertEquals(1, con2.commitCallCount)
        assertEquals(1, con2.closeCallCount)
        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `connection rollback and closed when two different transaction manager with nested transaction failed`() {
        val tm1 = newTransactionManager(cf1)
        val tm2 = newTransactionManager(cf2)
        val ex = RuntimeException("Application exception")
        try {
            tm1.executeAssert {
                tm2.executeAssert {
                    throw ex
                }
                assertEquals(
                    TransactionManager.currentOrNull()?.db?.let { TransactionManager.managerFor(it) },
                    TransactionManager.current().transactionManager
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                currentCoroutineContext().ensureActive()
            }
            assertEquals(e::class.java, ex::class.java)
            assertEquals(e.message, ex.message)
        }

        assertEquals(0, con2.commitCallCount)
        assertEquals(1, con2.rollbackCallCount)
        assertEquals(1, con2.closeCallCount)
        assertEquals(0, con1.commitCallCount)
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `transaction commit with transaction aware connection factory proxy`() {
        val transactionAwareCf = TransactionAwareConnectionFactoryProxy(cf1)
        val tm = newTransactionManager(transactionAwareCf)
        tm.executeAssert()

        assertTrue(con1.verifyCallOrder("setAutoCommit", "commit"))
        assertEquals(1, con1.commitCallCount)
        assertTrue(con1.closeCallCount > 0)
    }

    @Test
    suspend fun `transaction rollback with transaction aware connection factory proxy`() {
        val transactionAwareCf = TransactionAwareConnectionFactoryProxy(cf1)
        val tm = newTransactionManager(transactionAwareCf)
        val ex = RuntimeException("Application exception")
        try {
            tm.executeAssert {
                throw ex
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                currentCoroutineContext().ensureActive()
            }
            assertEquals(e::class.java, ex::class.java)
            assertEquals(e.message, ex.message)
        }

        assertTrue(con1.verifyCallOrder("setAutoCommit", "rollback"))
        assertEquals(1, con1.rollbackCallCount)
        assertTrue(con1.closeCallCount > 0)
    }

    @Test
    suspend fun `transaction with exception on rollback`() {
        con1.mockRollback = { throw R2dbcRollbackException("Rollback failure") }

        val tm = newTransactionManager(cf1)
        assertFailsWith<UncategorizedR2dbcException> {
            tm.executeAssert {
                assertEquals(false, it.isRollbackOnly)
                it.setRollbackOnly()
                assertEquals(true, it.isRollbackOnly)
            }
        }

        assertTrue(con1.verifyCallOrder("setAutoCommit", "rollback", "close"))
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `nested transaction with commit`() {
        val tm = newTransactionManager(cf1, R2dbcDatabaseConfig { useNestedTransactions = true })

        tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_NESTED) {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_NESTED)
            assertTrue(it.isNewTransaction)
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `nested transaction with rollback`() {
        val tm = newTransactionManager(cf1, R2dbcDatabaseConfig { useNestedTransactions = true })
        tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_NESTED) {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_NESTED) { status ->
                status.setRollbackOnly()
            }
            assertTrue(it.isNewTransaction)
        }

        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.releaseSavepointCallCount)
        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `requires new with commit`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW) { status ->
                assertTrue(status.isNewTransaction)
            }
            assertTrue(it.isNewTransaction)
        }

        assertEquals(2, con1.commitCallCount)
        assertEquals(2, con1.closeCallCount)
    }

    @Test
    suspend fun `requires new with inner rollback`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW) { status ->
                assertTrue(status.isNewTransaction)
                status.setRollbackOnly()
            }
            assertTrue(it.isNewTransaction)
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(2, con1.closeCallCount)
    }

    @Test
    suspend fun `not support with required transaction`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(
                initializeConnection = false,
                propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
            ) {
                assertFailsWith<IllegalStateException> {
                    TransactionManager.current().connection()
                }
            }
            assertTrue(it.isNewTransaction)
            TransactionManager.current().connection()
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `mandatory with transaction`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_MANDATORY)
            assertTrue(it.isNewTransaction)
            TransactionManager.current().connection()
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `mandatory without transaction`() {
        val tm = newTransactionManager(cf1)
        assertFailsWith<IllegalTransactionStateException> {
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_MANDATORY)
        }
    }

    @Test
    suspend fun `support with transaction`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_SUPPORTS)
            assertTrue(it.isNewTransaction)
            TransactionManager.current().connection()
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    suspend fun `support without transaction`() {
        val tm = newTransactionManager(cf1)
        assertFailsWith<IllegalStateException> {
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_SUPPORTS)
        }
        tm.executeAssert(initializeConnection = false, propagationBehavior = TransactionDefinition.PROPAGATION_SUPPORTS)
        assertEquals(0, con1.commitCallCount)
        assertEquals(0, con1.rollbackCallCount)
        assertEquals(0, con1.closeCallCount)
    }

    @Test
    suspend fun `transaction timeout`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert(initializeConnection = true, timeout = 1) {
            assertEquals(1, TransactionManager.current().queryTimeout)
        }
    }

    @Test
    suspend fun `transaction timeout propagation`() {
        val tm = newTransactionManager(cf1)
        tm.executeAssert(initializeConnection = true, timeout = 1) {
            tm.executeAssert(initializeConnection = true, timeout = 2) {
                assertEquals(1, TransactionManager.current().queryTimeout)
            }
            assertEquals(1, TransactionManager.current().queryTimeout)
        }
    }

    private fun newTransactionManager(
        connectionFactory: ConnectionFactory,
        databaseConfig: R2dbcDatabaseConfig.Builder = R2dbcDatabaseConfig.Builder()
    ): SpringReactiveTransactionManager = SpringReactiveTransactionManager(
        connectionFactory = connectionFactory,
        databaseConfig = databaseConfig.apply { explicitDialect = H2Dialect() }
    ).also { managers += it }

    private suspend fun SpringReactiveTransactionManager.executeAssert(
        initializeConnection: Boolean = true,
        propagationBehavior: Int = TransactionDefinition.PROPAGATION_REQUIRED,
        timeout: Int? = null,
        body: suspend (ReactiveTransaction) -> Unit = {}
    ) {
        val trxDef = DefaultTransactionDefinition(propagationBehavior).apply {
            if (timeout != null) this.timeout = timeout
        }
        val trxOp = TransactionalOperator.create(this, trxDef)
        withExposedReactiveContext {
            trxOp.executeAndAwait {
                TransactionManager.currentOrNull()?.db?.let { db ->
                    assertEquals(
                        TransactionManager.managerFor(db),
                        TransactionManager.current().transactionManager
                    )
                }

                if (initializeConnection) TransactionManager.current().connection()
                body(it)
            }
        }
    }
}
