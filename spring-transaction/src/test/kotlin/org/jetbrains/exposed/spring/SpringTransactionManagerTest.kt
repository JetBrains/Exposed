package org.jetbrains.exposed.spring

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import junit.framework.TestCase.assertEquals
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.assertFailsWith

class SpringTransactionManagerTest {

    private val ds1: DataSource = mockk()
    private val con1: Connection = mockk()
    private val ds2: DataSource = mockk()
    private val con2: Connection = mockk()

    @BeforeTest
    fun setup() {
        clearAllMocks()
        every { ds1.connection } returns con1
        every { ds2.connection } returns con2
        every { con1.commit() } returns Unit
        every { con1.rollback() } returns Unit
        every { con1.close() } returns Unit
        every { con2.commit() } returns Unit
        every { con2.rollback() } returns Unit
        every { con2.close() } returns Unit
    }

    @Test
    fun `set manager when transaction start`() {
        val tm = SpringTransactionManager(ds1)
        val tt = TransactionTemplate(tm)

        tt.executeWithoutResult {
            assertEquals(TransactionManager.managerFor(tm.getDatabase()), TransactionManager.manager)
        }
    }

    @Test
    fun `set right transaction manager when two transaction manager exist`() {
        val tm = SpringTransactionManager(ds1)
        val tt = TransactionTemplate(tm)

        tt.executeWithoutResult {
            assertEquals(TransactionManager.managerFor(tm.getDatabase()), TransactionManager.manager)
        }

        val tm2 = SpringTransactionManager(ds2)
        val tt2 = TransactionTemplate(tm2)

        tt2.executeWithoutResult {
            assertEquals(TransactionManager.managerFor(tm2.getDatabase()), TransactionManager.manager)
        }
    }

    @Test
    fun `set right transaction manager when two transaction manager with nested transaction template`() {
        val tm = SpringTransactionManager(ds1)
        val tt = TransactionTemplate(tm)
        val transactionManager = TransactionManager.managerFor(tm.getDatabase())

        val tm2 = SpringTransactionManager(ds2)
        val tt2 = TransactionTemplate(tm2)
        val transactionManager2 = TransactionManager.managerFor(tm2.getDatabase())

        tt2.executeWithoutResult {
            assertEquals(transactionManager2, TransactionManager.manager)
            tt.executeWithoutResult {
                assertEquals(transactionManager, TransactionManager.manager)
            }
            assertEquals(transactionManager2, TransactionManager.manager)
        }
    }

    @Test
    fun `connection commit and close when transaction success`() {
        every { con1.isReadOnly = false } returns Unit
        every { con1.autoCommit = false } returns Unit
        every { con1.isClosed } returns false
        every { con1.commit() } returns Unit
        every { con1.close() } returns Unit

        val tm = SpringTransactionManager(ds1)
        val tt = TransactionTemplate(tm)

        tt.executeWithoutResult {
            assertEquals(TransactionManager.managerFor(tm.getDatabase()), TransactionManager.manager)
            // for initialize connection
            TransactionManager.current().connection
        }
        verify { con1.commit() }
        verify { con1.close() }
    }

    @Test
    fun `connection rollback and close when transaction fail`() {
        every { con1.isReadOnly = false } returns Unit
        every { con1.autoCommit = false } returns Unit
        every { con1.isClosed } returns false

        val tm = SpringTransactionManager(ds1)
        val tt = TransactionTemplate(tm)

        val ex = RuntimeException("Application exception")
        try {
            tt.executeWithoutResult {
                assertEquals(TransactionManager.managerFor(tm.getDatabase()), TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
                throw ex
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }
        verify { con1.rollback() }
        verify { con1.close() }
    }

    @Test
    fun `connection commit and closed when nested transaction success`() {
        every { con1.isReadOnly = false } returns Unit
        every { con1.autoCommit = false } returns Unit
        every { con1.isClosed } returns false

        val tm = SpringTransactionManager(ds1)
        val tt = TransactionTemplate(tm)
        val transactionManager = TransactionManager.managerFor(tm.getDatabase())

        tt.executeWithoutResult {
            assertEquals(transactionManager, TransactionManager.manager)
            // for initialize connection
            TransactionManager.current().connection
            tt.executeWithoutResult {
                assertEquals(transactionManager, TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
            }
        }
        verify(exactly = 1) { con1.commit() }
        verify(exactly = 1) { con1.close() }
    }

    @Test
    fun `connection commit and closed when two different transaction manager with nested transaction success`() {
        every { con1.isReadOnly = false } returns Unit
        every { con1.autoCommit = false } returns Unit
        every { con1.isClosed } returns false

        val tm1 = SpringTransactionManager(ds1)
        val tt1 = TransactionTemplate(tm1)
        val transactionManager1 = TransactionManager.managerFor(tm1.getDatabase())

        every { con2.isReadOnly = false } returns Unit
        every { con2.autoCommit = false } returns Unit
        every { con2.isClosed } returns false

        val tm2 = SpringTransactionManager(ds2)
        val tt2 = TransactionTemplate(tm2)
        val transactionManager2 = TransactionManager.managerFor(tm2.getDatabase())

        tt1.executeWithoutResult {
            assertEquals(transactionManager1, TransactionManager.manager)
            // for initialize connection
            TransactionManager.current().connection
            tt2.executeWithoutResult {
                assertEquals(transactionManager2, TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
            }
            assertEquals(transactionManager1, TransactionManager.manager)
        }
        verify(exactly = 1) { con2.commit() }
        verify(exactly = 1) { con2.close() }
        verify(exactly = 1) { con1.commit() }
        verify(exactly = 1) { con1.close() }
    }

    @Test
    fun `connection rollback and closed when two different transaction manager with nested transaction failed`() {
        every { con1.isReadOnly = false } returns Unit
        every { con1.autoCommit = false } returns Unit
        every { con1.isClosed } returns false

        val tm1 = SpringTransactionManager(ds1)
        val tt1 = TransactionTemplate(tm1)
        val transactionManager1 = TransactionManager.managerFor(tm1.getDatabase())

        every { con2.isReadOnly = false } returns Unit
        every { con2.autoCommit = false } returns Unit
        every { con2.isClosed } returns false

        val tm2 = SpringTransactionManager(ds2)
        val tt2 = TransactionTemplate(tm2)
        val transactionManager2 = TransactionManager.managerFor(tm2.getDatabase())

        val ex = RuntimeException("Application exception")
        try {
            tt1.executeWithoutResult {
                assertEquals(transactionManager1, TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
                tt2.executeWithoutResult {
                    assertEquals(transactionManager2, TransactionManager.manager)
                    // for initialize connection
                    TransactionManager.current().connection
                    throw ex
                }
                assertEquals(transactionManager1, TransactionManager.manager)
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }
        verify(exactly = 0) { con2.commit() }
        verify(exactly = 1) { con2.rollback() }
        verify(exactly = 1) { con2.close() }
        verify(exactly = 0) { con1.commit() }
        verify(exactly = 1) { con1.rollback() }
        verify(exactly = 1) { con1.close() }
    }

    @Test
    fun `transaction commit with lazy connection data source proxy`() {
        every { con1.isClosed } returns false
        every { con1.autoCommit } returns false
        every { con1.transactionIsolation } returns Connection.TRANSACTION_READ_COMMITTED

        val lazyDs = LazyConnectionDataSourceProxy(ds1)

        val tm = SpringTransactionManager(lazyDs)
        val tt = TransactionTemplate(tm)

        tt.executeWithoutResult {
            assertEquals(TransactionManager.managerFor(tm.getDatabase()), TransactionManager.manager)
            // for initialize connection
            TransactionManager.current().connection
        }
        verify(exactly = 1) { con1.close() }
    }

    @Test
    fun `transaction rollback with lazy connection data source proxy`() {
        every { con1.isClosed } returns false
        every { con1.autoCommit } returns false
        every { con1.transactionIsolation } returns Connection.TRANSACTION_READ_COMMITTED

        val lazyDs = LazyConnectionDataSourceProxy(ds1)
        val tm = SpringTransactionManager(lazyDs)
        val tt = TransactionTemplate(tm)

        val ex = RuntimeException("Application exception")
        try {
            tt.executeWithoutResult {
                assertEquals(TransactionManager.managerFor(tm.getDatabase()), TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
                throw ex
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }
        verify(exactly = 1) { con1.close() }
    }

    @Test
    fun `transaction commit with transaction aware data source proxy`() {
        every { con1.isClosed } returns false
        every { con1.autoCommit } returns false
        every { con1.isReadOnly = false } returns Unit
        every { con1.autoCommit = false } returns Unit
        every { con1.transactionIsolation } returns Connection.TRANSACTION_READ_COMMITTED

        val transactionAwareDs = TransactionAwareDataSourceProxy(ds1)
        val tm = SpringTransactionManager(transactionAwareDs)
        val tt = TransactionTemplate(tm)

        tt.executeWithoutResult {
            assertEquals(TransactionManager.managerFor(tm.getDatabase()), TransactionManager.manager)
            // for initialize connection
            TransactionManager.current().connection
        }
        verifyOrder {
            con1.commit()
            con1.close()
        }
    }

    @Test
    fun `transaction rollback with transaction aware data source proxy`() {
        every { con1.isClosed } returns false
        every { con1.autoCommit } returns false
        every { con1.isReadOnly = false } returns Unit
        every { con1.autoCommit = false } returns Unit
        every { con1.transactionIsolation } returns Connection.TRANSACTION_READ_COMMITTED

        val transactionAwareDs = TransactionAwareDataSourceProxy(ds1)
        val tm = SpringTransactionManager(transactionAwareDs)
        val tt = TransactionTemplate(tm)

        val ex = RuntimeException("Application exception")
        try {
            tt.executeWithoutResult {
                assertEquals(TransactionManager.managerFor(tm.getDatabase()), TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
                throw ex
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }
        verifyOrder {
            con1.rollback()
            con1.close()
        }
    }

    @Test
    fun `transaction exception on commit and rollback on commit failure`() {
        every { con1.autoCommit } returns false
        every { con1.autoCommit = false } returns Unit
        every { con1.isReadOnly = false } returns Unit
        every { con1.isClosed } returns false
        every { con1.commit() } throws SQLException("Commit failure")

        val tm = SpringTransactionManager(ds1)
        tm.isRollbackOnCommitFailure = true
        val tt = TransactionTemplate(tm)

        assertFailsWith<TransactionSystemException> {
            tt.executeWithoutResult {
                assertEquals(TransactionManager.managerFor(tm.getDatabase()), TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
            }
        }
        verifyOrder {
            con1.commit()
            con1.rollback()
            con1.close()
        }
    }

    @Test
    fun `transaction with exception on rollback`() {
        every { con1.autoCommit } returns false
        every { con1.autoCommit = false } returns Unit
        every { con1.isReadOnly = false } returns Unit
        every { con1.isClosed } returns false
        every { con1.rollback() } throws SQLException("Rollback failure")

        val tm = SpringTransactionManager(ds1)
        val tt = TransactionTemplate(tm)

        assertFailsWith<TransactionSystemException> {
            tt.executeWithoutResult {
                assertEquals(TransactionManager.managerFor(tm.getDatabase()), TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
                assertEquals(false, it.isRollbackOnly)
                it.setRollbackOnly()
                assertEquals(true, it.isRollbackOnly)
            }
        }
        verifyOrder {
            con1.isReadOnly = false
            con1.autoCommit = false
            con1.isClosed
            con1.rollback()
        }
        verify { con1.close() }
    }
}
