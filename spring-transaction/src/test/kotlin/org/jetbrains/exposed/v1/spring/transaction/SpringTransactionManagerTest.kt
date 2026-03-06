package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpringTransactionManagerTest {

    private val ds1 = DataSourceSpy(::ConnectionSpy)
    private val con1 = ds1.con as ConnectionSpy
    private val ds2 = DataSourceSpy(::ConnectionSpy)
    private val con2 = ds2.con as ConnectionSpy

    /**
     * This test has a teardown that unregisters databases. The intention
     * is to only tear down databases registered by this test - but
     * an earlier implementation accidentally unregistered the main
     * database registered by the [SpringTransactionTestBase].
     *
     * To avoid doing such, we try to only unregister until we hit the
     * one that was there before the start of this test.
     */
    private var databaseBeforeTestStart: Database? = null

    @BeforeEach
    fun beforeTest() {
        databaseBeforeTestStart = TransactionManager.primaryDatabase
        con1.clearMock()
        con2.clearMock()
    }

    @AfterEach
    fun afterTest() {
        while (TransactionManager.primaryDatabase != databaseBeforeTestStart) {
            TransactionManager.primaryDatabase?.let { TransactionManager.closeAndUnregister(it) }
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
                TransactionManager.currentOrNull()?.db?.let { TransactionManager.managerFor(it) },
                TransactionManager.current().transactionManager
            )
        }
    }

    @Test
    fun `connection commit and close when transaction success`() {
        val tm = SpringTransactionManager(ds1)
        tm.executeAssert()

        assertTrue(con1.verifyCallOrder("setAutoCommit", "commit", "close"))
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

        assertTrue(con1.verifyCallOrder("setAutoCommit", "commit"))
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

        assertTrue(con1.verifyCallOrder("setAutoCommit", "rollback"))
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

        assertTrue(con1.verifyCallOrder("setAutoCommit", "commit", "isClosed", "rollback", "close"))
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

        assertTrue(con1.verifyCallOrder("setAutoCommit", "isClosed", "rollback", "close"))
        assertEquals(1, con1.rollbackCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `nested transaction with commit`() {
        val tm = SpringTransactionManager(ds1, DatabaseConfig { useNestedTransactions = true })

        tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_NESTED) {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_NESTED)
            assertTrue(it.isNewTransaction)
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `nested transaction with rollback`() {
        val tm = SpringTransactionManager(ds1, DatabaseConfig { useNestedTransactions = true })
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
    fun `requires new with commit`() {
        val tm = SpringTransactionManager(ds1)
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
    fun `requires new with inner rollback`() {
        val tm = SpringTransactionManager(ds1)
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
    fun `not support with required transaction`() {
        val tm = SpringTransactionManager(ds1)
        tm.executeAssert {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(
                initializeConnection = false,
                propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
            ) {
                assertFailsWith<IllegalStateException> {
                    TransactionManager.current().connection
                }
            }
            assertTrue(it.isNewTransaction)
            TransactionManager.current().connection
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `mandatory with transaction`() {
        val tm = SpringTransactionManager(ds1)
        tm.executeAssert {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_MANDATORY)
            assertTrue(it.isNewTransaction)
            TransactionManager.current().connection
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `mandatory without transaction`() {
        val tm = SpringTransactionManager(ds1)
        assertFailsWith<IllegalTransactionStateException> {
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_MANDATORY)
        }
    }

    @Test
    fun `support with transaction`() {
        val tm = SpringTransactionManager(ds1)
        tm.executeAssert {
            assertTrue(it.isNewTransaction)
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_SUPPORTS)
            assertTrue(it.isNewTransaction)
            TransactionManager.current().connection
        }

        assertEquals(1, con1.commitCallCount)
        assertEquals(1, con1.closeCallCount)
    }

    @Test
    fun `support without transaction`() {
        val tm = SpringTransactionManager(ds1)
        assertFailsWith<IllegalStateException> {
            tm.executeAssert(propagationBehavior = TransactionDefinition.PROPAGATION_SUPPORTS)
        }
        tm.executeAssert(initializeConnection = false, propagationBehavior = TransactionDefinition.PROPAGATION_SUPPORTS)
        assertEquals(0, con1.commitCallCount)
        assertEquals(0, con1.rollbackCallCount)
        assertEquals(0, con1.closeCallCount)
    }

    @Test
    fun `transaction timeout`() {
        val tm = SpringTransactionManager(ds1)
        tm.executeAssert(initializeConnection = true, timeout = 1) {
            assertEquals(1, TransactionManager.current().queryTimeout)
        }
    }

    @Test
    fun `transaction timeout propagation`() {
        val tm = SpringTransactionManager(ds1)
        tm.executeAssert(initializeConnection = true, timeout = 1) {
            tm.executeAssert(initializeConnection = true, timeout = 2) {
                assertEquals(1, TransactionManager.current().queryTimeout)
            }
            assertEquals(1, TransactionManager.current().queryTimeout)
        }
    }

    private fun SpringTransactionManager.executeAssert(
        initializeConnection: Boolean = true,
        propagationBehavior: Int = TransactionDefinition.PROPAGATION_REQUIRED,
        timeout: Int? = null,
        body: (TransactionStatus) -> Unit = {}
    ) {
        val tt = TransactionTemplate(this)
        tt.propagationBehavior = propagationBehavior
        if (timeout != null) tt.timeout = timeout
        tt.executeWithoutResult {
            TransactionManager.currentOrNull()?.db?.let { db ->
                assertEquals(
                    TransactionManager.managerFor(db),
                    TransactionManager.current().transactionManager
                )
            }

            if (initializeConnection) TransactionManager.current().connection
            body(it)
        }
    }
}
