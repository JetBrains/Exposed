package org.jetbrains.exposed.v1.spring.reactive.transaction

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.junit.Before
import org.junit.Test
import org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import org.springframework.transaction.support.DefaultTransactionDefinition
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpringReactiveTransactionManagerTest {

    private val cf1 = ConnectionFactorySpy(::ConnectionSpy)
    private lateinit var con1: ConnectionSpy
    private val cf2 = ConnectionFactorySpy(::ConnectionSpy)
    private lateinit var con2: ConnectionSpy

    @Before
    fun init() = runTest {
        con1 = cf1.getCon() as ConnectionSpy
        con2 = cf2.getCon() as ConnectionSpy
    }

    @BeforeTest
    fun beforeTest() {
        con1.clearMock()
        con2.clearMock()
    }

    @AfterTest
    fun afterTest() {
        while (TransactionManager.defaultDatabase != null) {
            TransactionManager.defaultDatabase?.let { TransactionManager.closeAndUnregister(it) }
        }
    }

    @Test
    fun `set manager when transaction start`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
        tm.executeAssert(false)
    }

    @Test
    fun `set right transaction manager when two transaction manager exist`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
        tm.executeAssert(false)

        val tm2 = SpringReactiveTransactionManager(cf2)
        tm2.executeAssert(false)
    }

    @Test
    fun `set right transaction manager when two transaction manager with nested transaction template`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
        val tm2 = SpringReactiveTransactionManager(cf2)

        tm2.executeAssert(false) {
            tm.executeAssert(false)

            assertEquals(
                TransactionManager.currentOrNull()?.db?.let { TransactionManager.managerFor(it) },
                TransactionManager.current().transactionManager
            )
        }
    }

    @Test
    fun `connection commit and close when transaction success`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
        tm.executeAssert()

        assertTrue(con1.verifyCallOrder("setAutoCommit", "commit", "close"))
        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `connection rollback and close when transaction fail`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
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
    fun `connection commit and closed when nested transaction success`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
        tm.executeAssert {
            tm.executeAssert()
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `connection commit and closed when two different transaction manager with nested transaction success`() = runTest {
        val tm1 = SpringReactiveTransactionManager(cf1)
        val tm2 = SpringReactiveTransactionManager(cf2)

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
    fun `connection rollback and closed when two different transaction manager with nested transaction failed`() = runTest {
        val tm1 = SpringReactiveTransactionManager(cf1)
        val tm2 = SpringReactiveTransactionManager(cf2)
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
            assertEquals(ex, e)
        }

        assertEquals(0, con2.commitCallCount)
        assertEquals(1, con2.rollbackCallCount)
        assertEquals(1, con2.closeCallCount)
        assertEquals(0, con1.commitCallCount)
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    // https://github.com/spring-projects/spring-framework/issues/33897
//    @Test
//    fun `transaction commit with lazy connection data source proxy`() = runTest {
//        val lazyDs = LazyConnectionDataSourceProxy(cf1)
//        val tm = SpringReactiveTransactionManager(lazyDs)
//        tm.executeAssert()
//
//        assertEquals(1, con1.closeCallCount)
//    }

    // https://github.com/spring-projects/spring-framework/issues/33897
//    @Test
//    fun `transaction rollback with lazy connection data source proxy`() = runTest {
//        val lazyDs = LazyConnectionDataSourceProxy(cf1)
//        val tm = SpringReactiveTransactionManager(lazyDs)
//        val ex = RuntimeException("Application exception")
//        try {
//            tm.executeAssert {
//                throw ex
//            }
//        } catch (e: Exception) {
//            assertEquals(ex, e)
//        }
//        assertEquals(1, con1.closeCallCount)
//    }

    @Test
    fun `transaction commit with transaction aware connection factory proxy`() = runTest {
        val transactionAwareCf = TransactionAwareConnectionFactoryProxy(cf1)
        val tm = SpringReactiveTransactionManager(transactionAwareCf)
        tm.executeAssert()

        assertTrue(con1.verifyCallOrder("setAutoCommit", "commit"))
        assertEquals(1, con1.commitCallCount)
        assertTrue(con1.closeCallCount > 0)
    }

    @Test
    fun `transaction rollback with transaction aware connection factory proxy`() = runTest {
        val transactionAwareCf = TransactionAwareConnectionFactoryProxy(cf1)
        val tm = SpringReactiveTransactionManager(transactionAwareCf)
        val ex = RuntimeException("Application exception")
        try {
            tm.executeAssert {
                throw ex
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }

        assertTrue(con1.verifyCallOrder("setAutoCommit", "rollback"))
        assertEquals(1, con1.rollbackCallCount)
        assertTrue(con1.closeCallCount > 0)
    }

    @Test
    fun `transaction exception on commit and rollback on commit failure`() = runTest {
        con1.mockCommit = { throw SQLException("Commit failure") }

        val tm = SpringReactiveTransactionManager(cf1)
//        tm.isRollbackOnCommitFailure = true
        assertFailsWith<TransactionSystemException> {
            tm.executeAssert()
        }

        assertTrue(con1.verifyCallOrder("setAutoCommit", "commit", "isClosed", "rollback", "close"))
        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `transaction with exception on rollback`() = runTest {
        con1.mockRollback = { throw SQLException("Rollback failure") }

        val tm = SpringReactiveTransactionManager(cf1)
        assertFailsWith<TransactionSystemException> {
            tm.executeAssert {
                assertEquals(false, it.isRollbackOnly)
                it.setRollbackOnly()
                assertEquals(true, it.isRollbackOnly)
            }
        }

        assertTrue(con1.verifyCallOrder("setAutoCommit", "isClosed", "rollback", "close"))
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `nested transaction with commit`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1, R2dbcDatabaseConfig { useNestedTransactions = true })

        tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_NESTED) {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_NESTED)
            assertTrue(it.isNewTransaction)
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `nested transaction with rollback`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1, R2dbcDatabaseConfig { useNestedTransactions = true })
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
    fun `requires new with commit`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
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
    fun `requires new with inner rollback`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
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
    fun `not support with required transaction`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
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
    fun `mandatory with transaction`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
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
    fun `mandatory without transaction`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
        assertFailsWith<IllegalTransactionStateException> {
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_MANDATORY)
        }
    }

    @Test
    fun `support with transaction`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
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
    fun `support without transaction`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
        assertFailsWith<IllegalStateException> {
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_SUPPORTS)
        }
        tm.executeAssert(initializeConnection = false, propagationBehavior = TransactionDefinition.PROPAGATION_SUPPORTS)
        assertEquals(0, con1.commitCallCount)
        assertEquals(0, con1.rollbackCallCount)
        assertEquals(0, con1.closeCallCount)
    }

    @Test
    fun `transaction timeout`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
        tm.executeAssert(initializeConnection = true, timeout = 1) {
            assertEquals(1, TransactionManager.current().queryTimeout)
        }
    }

    @Test
    fun `transaction timeout propagation`() = runTest {
        val tm = SpringReactiveTransactionManager(cf1)
        tm.executeAssert(initializeConnection = true, timeout = 1) {
            tm.executeAssert(initializeConnection = true, timeout = 2) {
                assertEquals(1, TransactionManager.current().queryTimeout)
            }
            assertEquals(1, TransactionManager.current().queryTimeout)
        }
    }

    private suspend fun ReactiveTransactionManager.executeAssert(
        initializeConnection: Boolean = true,
        propagationBehavior: Int = TransactionDefinition.PROPAGATION_REQUIRED,
        timeout: Int? = null,
        body: suspend (ReactiveTransaction) -> Unit = {}
    ) {
        val td = DefaultTransactionDefinition(propagationBehavior).apply {
            if (timeout != null) this.timeout = timeout
        }
        val to = TransactionalOperator.create(this, td)
        to.executeAndAwait {
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
