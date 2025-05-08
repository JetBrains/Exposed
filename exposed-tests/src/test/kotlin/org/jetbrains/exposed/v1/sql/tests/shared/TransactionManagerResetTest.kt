package org.jetbrains.exposed.v1.sql.tests.shared

import junit.framework.TestCase.assertSame
import org.jetbrains.exposed.v1.sql.tests.LogDbInTestName
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.transactions.TransactionManager
import org.jetbrains.exposed.v1.sql.transactions.transaction
import org.junit.Assume
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Ignore
import kotlin.test.assertEquals

class TransactionManagerResetTest : LogDbInTestName() {
    /**
     * When the test is running alone it will have NonInitializedTransactionManager as a manager.
     * After the first connection is established, the manager will be initialized and get back after the connection is closed.
     *
     * When the test is running in a suite, the manager will be initialized before the first connection is established.
     * After the first connect it will, not change because the first manager will be used by default.
     *
     * This tests depends on the order of tests in the suite, so it will be disabled until we find a better solution.
     */
    @Test
    @Ignore
    fun `test closeAndUnregister with next Database-connect works fine`() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val fail = AtomicReference<Throwable?>(null)
        thread {
            try {
                val initialManager = TransactionManager.manager
                val db1 = TestDB.H2_V2.connect()
                val db1TransactionManager = TransactionManager.managerFor(db1)

                val afterDb1Connect = TransactionManager.manager
                assertSame(db1TransactionManager, afterDb1Connect)

                transaction(db1) {
                    assertEquals(db1TransactionManager, TransactionManager.manager)
                    exec("SELECT 1 from dual;")
                }

                TransactionManager.closeAndUnregister(db1)
                assertSame(initialManager, TransactionManager.manager)
                val db2 = TestDB.H2_V2.connect()

                // Check should be made in a separate thread as in current thread manager is already initialized
                thread {
                    try {
                        assertEquals(TransactionManager.managerFor(db2), TransactionManager.manager)
                    } catch (cause: Throwable) {
                        fail.set(cause)
                        throw cause
                    } finally {
                        TransactionManager.closeAndUnregister(db2)
                    }
                }.join()
            } catch (cause: Throwable) {
                fail.set(cause)
                throw cause
            }
        }.join()

        fail.get()?.let { throw it }
    }
}
