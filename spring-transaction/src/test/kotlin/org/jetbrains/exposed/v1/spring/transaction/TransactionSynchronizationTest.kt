package org.jetbrains.exposed.v1.spring.transaction

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

class TransactionSynchronizationTest : SpringTransactionTestBase() {

    private var synchronization: TestSynchronization = TestSynchronization()

    @BeforeEach
    fun setUp() {
        synchronization = TestSynchronization()
    }

    @Test
    fun testCommitIsReported() {
        transactionManager.execute {
            TransactionSynchronizationManager.registerSynchronization(synchronization)
        }

        assertTrue(synchronization.committed)
    }

    @Test
    fun testRollbackIsReported() {
        transactionManager.execute {
            TransactionSynchronizationManager.registerSynchronization(synchronization)
            it.setRollbackOnly()
        }

        assertTrue(synchronization.rolledBack)
    }

    @Test
    fun testNestedRolledBackIsStillCommit() {
        transactionManager.execute {
            TransactionSynchronizationManager.registerSynchronization(synchronization)
            transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
                it.setRollbackOnly()
            }
        }

        assertTrue(synchronization.committed)
    }

    @Test
    fun testRequiresNewRolledBackIsStillCommit() {
        transactionManager.execute {
            TransactionSynchronizationManager.registerSynchronization(synchronization)
            transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
                it.setRollbackOnly()
            }
        }

        assertTrue(synchronization.committed)
    }

    @Test
    fun testOuterRollbackPropagatedToNested() {
        transactionManager.execute {
            it.setRollbackOnly()
            transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
                TransactionSynchronizationManager.registerSynchronization(synchronization)
            }
        }

        assertTrue(synchronization.rolledBack)
    }

    @Test
    fun testOuterRollbackNotPropagatedToRequiresNew() {
        transactionManager.execute {
            it.setRollbackOnly()
            transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
                TransactionSynchronizationManager.registerSynchronization(synchronization)
            }
        }

        assertTrue(synchronization.committed)
    }

    class TestSynchronization : TransactionSynchronization {

        var committed: Boolean = false
        var rolledBack: Boolean = false

        override fun afterCompletion(status: Int) {
            committed = (status == TransactionSynchronization.STATUS_COMMITTED)
            rolledBack = (status == TransactionSynchronization.STATUS_ROLLED_BACK)
        }
    }
}
