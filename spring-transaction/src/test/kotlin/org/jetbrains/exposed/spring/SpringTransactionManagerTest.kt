package org.jetbrains.exposed.spring

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import junit.framework.TestCase.assertEquals
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.assertFailsWith

class SpringTransactionManagerTest {

    @Test
    fun `set manager when transaction start`() {
        val dataSource: DataSource = mockk()
        val tm = SpringTransactionManager(dataSource)
        val tt = TransactionTemplate(tm)

        val database = tm.getDatabase()
        val transactionManager = TransactionManager.managerFor(database)

        tt.executeWithoutResult {
            assertEquals(transactionManager, TransactionManager.manager)
        }
    }

    @Test
    fun `set right transaction manager when two transaction manager exist`() {
        val dataSource: DataSource = mockk()
        val tm = SpringTransactionManager(dataSource)
        val tt = TransactionTemplate(tm)

        val database = tm.getDatabase()
        val transactionManager = TransactionManager.managerFor(database)

        tt.executeWithoutResult {
            assertEquals(transactionManager, TransactionManager.manager)
        }

        val dataSource2: DataSource = mockk()
        val tm2 = SpringTransactionManager(dataSource2)
        val tt2 = TransactionTemplate(tm2)

        val database2 = tm2.getDatabase()
        val transactionManager2 = TransactionManager.managerFor(database2)

        tt2.executeWithoutResult {
            assertEquals(transactionManager2, TransactionManager.manager)
        }
    }

    @Test
    fun `set right transaction manager when two transaction manager with nested transaction template`() {
        val dataSource1: DataSource = mockk()
        val tm = SpringTransactionManager(dataSource1)
        val tt = TransactionTemplate(tm)

        val database = tm.getDatabase()
        val transactionManager = TransactionManager.managerFor(database)

        val dataSource2: DataSource = mockk()
        val tm2 = SpringTransactionManager(dataSource2)
        val tt2 = TransactionTemplate(tm2)

        val database2 = tm2.getDatabase()
        val transactionManager2 = TransactionManager.managerFor(database2)

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
        val ds: DataSource = mockk()
        val con: Connection = mockk()
        every { ds.connection } returns con
        every { con.isReadOnly = false } returns Unit
        every { con.autoCommit = false } returns Unit
        every { con.isClosed } returns false
        every { con.commit() } returns Unit
        every { con.close() } returns Unit

        val tm = SpringTransactionManager(ds)
        val tt = TransactionTemplate(tm)

        val database = tm.getDatabase()
        val transactionManager = TransactionManager.managerFor(database)

        tt.executeWithoutResult {
            assertEquals(transactionManager, TransactionManager.manager)
            // for initialize connection
            TransactionManager.current().connection
        }
        verify { con.commit() }
        verify { con.close() }
    }

    @Test
    fun `connection rollback and close when transaction fail`() {
        val ds: DataSource = mockk()
        val con: Connection = mockk()
        every { ds.connection } returns con
        every { con.isReadOnly = false } returns Unit
        every { con.autoCommit = false } returns Unit
        every { con.isClosed } returns false
        every { con.rollback() } returns Unit
        every { con.close() } returns Unit

        val tm = SpringTransactionManager(ds)
        val tt = TransactionTemplate(tm)

        val database = tm.getDatabase()
        val transactionManager = TransactionManager.managerFor(database)

        val ex = RuntimeException("Application exception")
        try {
            tt.executeWithoutResult {
                assertEquals(transactionManager, TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
                throw ex
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }
        verify { con.rollback() }
        verify { con.close() }
    }

    @Test
    fun `connection commit and closed when nested transaction success`() {
        val ds: DataSource = mockk()
        val con: Connection = mockk()
        every { ds.connection } returns con
        every { con.isReadOnly = false } returns Unit
        every { con.autoCommit = false } returns Unit
        every { con.isClosed } returns false
        every { con.commit() } returns Unit
        every { con.close() } returns Unit

        val tm = SpringTransactionManager(ds)
        val tt = TransactionTemplate(tm)

        val database = tm.getDatabase()
        val transactionManager = TransactionManager.managerFor(database)

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
        verify(exactly = 1) { con.commit() }
        verify(exactly = 1) { con.close() }
    }

    @Test
    fun `connection commit and closed when two different transaction manager with nested transaction success`() {
        val ds1: DataSource = mockk()
        val con1: Connection = mockk()
        every { ds1.connection } returns con1
        every { con1.isReadOnly = false } returns Unit
        every { con1.autoCommit = false } returns Unit
        every { con1.isClosed } returns false
        every { con1.commit() } returns Unit
        every { con1.close() } returns Unit

        val tm1 = SpringTransactionManager(ds1)
        val tt1 = TransactionTemplate(tm1)

        val database1 = tm1.getDatabase()
        val transactionManager1 = TransactionManager.managerFor(database1)

        val ds2: DataSource = mockk()
        val con2: Connection = mockk()
        every { ds2.connection } returns con2
        every { con2.isReadOnly = false } returns Unit
        every { con2.autoCommit = false } returns Unit
        every { con2.isClosed } returns false
        every { con2.commit() } returns Unit
        every { con2.close() } returns Unit

        val tm2 = SpringTransactionManager(ds2)
        val tt2 = TransactionTemplate(tm2)

        val database2 = tm2.getDatabase()
        val transactionManager2 = TransactionManager.managerFor(database2)

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
        val ds1: DataSource = mockk()
        val con1: Connection = mockk()
        every { ds1.connection } returns con1
        every { con1.isReadOnly = false } returns Unit
        every { con1.autoCommit = false } returns Unit
        every { con1.isClosed } returns false
        every { con1.rollback() } returns Unit
        every { con1.close() } returns Unit

        val tm1 = SpringTransactionManager(ds1)
        val tt1 = TransactionTemplate(tm1)

        val database1 = tm1.getDatabase()
        val transactionManager1 = TransactionManager.managerFor(database1)

        val ds2: DataSource = mockk()
        val con2: Connection = mockk()
        every { ds2.connection } returns con2
        every { con2.isReadOnly = false } returns Unit
        every { con2.autoCommit = false } returns Unit
        every { con2.isClosed } returns false
        every { con2.rollback() } returns Unit
        every { con2.close() } returns Unit

        val tm2 = SpringTransactionManager(ds2)
        val tt2 = TransactionTemplate(tm2)

        val database2 = tm2.getDatabase()
        val transactionManager2 = TransactionManager.managerFor(database2)

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
        val ds: DataSource = mockk()
        val con: Connection = mockk()
        every { ds.connection } returns con
        every { con.isClosed } returns false
        every { con.autoCommit } returns false
        every { con.transactionIsolation } returns Connection.TRANSACTION_READ_COMMITTED
        every { con.close() } returns Unit

        val lazyDs = LazyConnectionDataSourceProxy(ds)
        val tm = SpringTransactionManager(lazyDs)
        val tt = TransactionTemplate(tm)

        val database = tm.getDatabase()
        val transactionManager = TransactionManager.managerFor(database)

        tt.executeWithoutResult {
            assertEquals(transactionManager, TransactionManager.manager)
            // for initialize connection
            TransactionManager.current().connection
        }
        verify(exactly = 1) { con.close() }
    }

    @Test
    fun `transaction rollback with lazy connection data source proxy`() {
        val ds: DataSource = mockk()
        val con: Connection = mockk()
        every { ds.connection } returns con
        every { con.isClosed } returns false
        every { con.autoCommit } returns false
        every { con.transactionIsolation } returns Connection.TRANSACTION_READ_COMMITTED
        every { con.close() } returns Unit

        val lazyDs = LazyConnectionDataSourceProxy(ds)
        val tm = SpringTransactionManager(lazyDs)
        val tt = TransactionTemplate(tm)

        val database = tm.getDatabase()
        val transactionManager = TransactionManager.managerFor(database)

        val ex = RuntimeException("Application exception")
        try {
            tt.executeWithoutResult {
                assertEquals(transactionManager, TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
                throw ex
            }
        } catch (e: Exception) {
            assertEquals(ex, e)
        }
        verify(exactly = 1) { con.close() }
    }

    @Test
    fun `transaction exception on commit and rollback on commit failure`() {
        val ds: DataSource = mockk()
        val con: Connection = mockk()
        every { ds.connection } returns con
        every { con.autoCommit } returns false
        every { con.autoCommit = false } returns Unit
        every { con.isReadOnly = false } returns Unit
        every { con.isClosed } returns false
        every { con.commit() } throws SQLException("Commit failure")
        every { con.rollback() } returns Unit
        every { con.close() } returns Unit

        val tm = SpringTransactionManager(ds)
        tm.isRollbackOnCommitFailure = true
        val tt = TransactionTemplate(tm)

        val database = tm.getDatabase()
        val transactionManager = TransactionManager.managerFor(database)

        assertFailsWith<TransactionSystemException> {
            tt.executeWithoutResult {
                assertEquals(transactionManager, TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
            }
        }
        verifyOrder {
            con.commit()
            con.rollback()
            con.close()
        }
    }

    @Test
    fun `transaction with exception on rollback`() {
        val ds: DataSource = mockk()
        val con: Connection = mockk()
        every { ds.connection } returns con
        every { con.autoCommit } returns false
        every { con.autoCommit = false } returns Unit
        every { con.isReadOnly = false } returns Unit
        every { con.isClosed } returns false
        every { con.commit() } returns Unit
        every { con.rollback() } throws SQLException("Rollback failure")
        every { con.close() } returns Unit

        val tm = SpringTransactionManager(ds)
        val tt = TransactionTemplate(tm)

        val database = tm.getDatabase()
        val transactionManager = TransactionManager.managerFor(database)

        assertFailsWith<TransactionSystemException> {
            tt.executeWithoutResult {
                assertEquals(transactionManager, TransactionManager.manager)
                // for initialize connection
                TransactionManager.current().connection
                assertEquals(false, it.isRollbackOnly)
                it.setRollbackOnly()
                assertEquals(true, it.isRollbackOnly)
            }
        }
        verifyOrder {
            con.isReadOnly = false
            con.autoCommit = false
            con.isClosed
            con.rollback()
        }
        verify { con.close() }
    }
}
