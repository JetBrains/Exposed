package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

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
    fun testNestedAfterCommitFailureStillReportsCommit() {
        val jdbc = JdbcTemplate(ctx.getBean(DataSource::class.java))
        jdbc.execute("CREATE TABLE after_commit_test (id INT)")
        try {
            val failure = IllegalStateException("Nested afterCommit failed")
            var rollbackCalls = 0
            val thrown = assertFailsWith<RuntimeException> {
                transactionManager.execute {
                    TransactionSynchronizationManager.registerSynchronization(synchronization)
                    TransactionManager.current().registerInterceptor(object : StatementInterceptor {
                        override fun afterRollback(transaction: Transaction) {
                            rollbackCalls++
                        }
                    })
                    transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
                        jdbc.update("INSERT INTO after_commit_test VALUES (1)")
                        TransactionManager.current().registerInterceptor(object : StatementInterceptor {
                            override fun afterCommit(transaction: Transaction) {
                                throw failure
                            }
                        })
                    }
                }
            }

            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM after_commit_test", Int::class.java))
            assertTrue(synchronization.committed)
            assertEquals(0, rollbackCalls)
            assertSame(failure, thrown)
        } finally {
            jdbc.execute("DROP TABLE after_commit_test")
        }
    }

    @Test
    fun testNestedAfterCommitRunsWhenSpringSynchronizationFails() {
        val failure = IllegalStateException("Spring afterCommit failed")
        var nestedCommitCalls = 0

        val thrown = assertFailsWith<IllegalStateException> {
            transactionManager.execute {
                TransactionSynchronizationManager.registerSynchronization(synchronization)
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCommit() {
                        throw failure
                    }
                })
                transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
                    TransactionManager.current().registerInterceptor(object : StatementInterceptor {
                        override fun afterCommit(transaction: Transaction) {
                            nestedCommitCalls++
                        }
                    })
                }
            }
        }

        assertSame(failure, thrown)
        assertTrue(synchronization.committed)
        assertEquals(1, nestedCommitCalls)
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
