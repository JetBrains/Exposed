package org.jetbrains.exposed.v1.spring7.reactive.transaction

import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.r2dbc.core.awaitSingle
import org.springframework.test.annotation.Commit
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.TransactionDefinition
import java.util.*
import kotlin.test.assertFailsWith

/**
 * Similar to [ExposedTransactionManagerTest] but working with Spring R2DBC
 * constructs like [DatabaseClient] instead of Exposed APIs to verify that it
 * works like expected.
 */
open class R2dbcExposedTransactionManagerTest : SpringReactiveTransactionTestBase() {
    object T1 : Table() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    @Autowired
    private lateinit var connectionFactory: ConnectionFactory

    private val r2dbc: DatabaseClient by lazy { DatabaseClient.create(connectionFactory) }

    private suspend fun insertRandom() {
        r2dbc.sql(
            "INSERT INTO ${T1.tableName} VALUES (:value)"
        ).bind(
            "value", Random().nextInt().toString()
        ).fetch().awaitRowsUpdated()
    }

    private suspend fun insert(value: String) {
        r2dbc.sql(
            "INSERT INTO ${T1.tableName} VALUES (:value)"
        ).bind(
            "value", value
        ).fetch().awaitRowsUpdated()
    }

    private suspend fun getCount(): Int = r2dbc
        .sql("SELECT count(*) FROM ${T1.tableName}")
        .map { row, _ ->
            row.get(0, java.lang.Long::class.java)?.toInt() ?: 0
        }
        .awaitSingle()

    private suspend fun getSingleValue(): String = r2dbc
        .sql("SELECT * FROM ${T1.tableName}")
        .map { row, _ -> row.get("c1", String::class.java) ?: "" }
        .awaitSingle()

    @BeforeEach
    fun beforeTest() = runTest {
        transactionManager.execute {
            SchemaUtils.create(T1)
        }
    }

    @AfterEach
    fun afterTest() = runTest {
        transactionManager.execute {
            SchemaUtils.drop(T1)
        }
    }

    @RepeatedTest(5)
    //    @Transactional // see [runTestWithMockTransactional]
    @Commit
    open fun testConnection() = runTestWithMockTransactional {
        insertRandom()
        assertEquals(1, getCount())
    }

    @RepeatedTest(5)
    //    @Transactional // see [runTestWithMockTransactional]
    @Commit
    open fun testConnection2() = runTestWithMockTransactional {
        val rnd = Random().nextInt().toString()
        insert(rnd)
        assertEquals(rnd, getSingleValue())
    }

    @RepeatedTest(5)
    @Commit
    open fun testConnectionCombineWithExposedTransaction() = runTest {
        suspendTransaction {
            val rnd = Random().nextInt().toString()
            insert(rnd)
            assertEquals(rnd, getSingleValue())

            this@R2dbcExposedTransactionManagerTest.transactionManager.execute {
                insertRandom()
                assertEquals(2, getCount())
            }
        }
    }

    // TODO - This (& only this test?) fails because of line 114 in suspendTransaction();
    // If the line is reverted to original, it passes -> ThreadLocalTransactionsStack.getTransactionOrNull(databaseToUse)
    @RepeatedTest(5)
    @Commit
//    @Transactional // see [runTestWithMockTransactional]
    open fun testConnectionCombineWithExposedTransaction2() = runTestWithMockTransactional {
        val rnd = Random().nextInt().toString()
        insert(rnd)
        assertEquals(rnd, getSingleValue())

        suspendTransaction {
            insertRandom()
            assertEquals(2, getCount())
        }
    }

    /**
     * Test for Propagation.NESTED
     * Execute within a nested transaction if a current transaction exists, behave like REQUIRED otherwise.
     */
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    open fun testConnectionWithNestedTransactionCommit() = runTestWithMockTransactional {
        insertRandom()
        assertEquals(1, getCount())
        transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
            insertRandom()
            assertEquals(2, getCount())
        }
        assertEquals(2, getCount())
    }

    /**
     * Test for Propagation.NESTED with inner roll-back
     * The nested transaction will be roll-back only inner transaction when the transaction marks as rollback.
     */
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    open fun testConnectionWithNestedTransactionInnerRollback() = runTestWithMockTransactional {
        insertRandom()
        assertEquals(1, getCount())
        transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) { status ->
            insertRandom()
            assertEquals(2, getCount())
            status.setRollbackOnly()
        }
        assertEquals(1, getCount())
    }

    /**
     * Test for Propagation.NESTED with outer roll-back
     * The nested transaction will be roll-back entire transaction when the transaction marks as rollback.
     */
    @RepeatedTest(5)
    fun testConnectionWithNestedTransactionOuterRollback() = runTest {
        transactionManager.execute {
            insertRandom()
            assertEquals(1, getCount())
            it.setRollbackOnly()

            transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
                insertRandom()
                assertEquals(2, getCount())
            }
            assertEquals(2, getCount())
        }

        transactionManager.execute {
            assertEquals(0, getCount())
        }
    }

    // TODO
    /**
     * Test for Propagation.REQUIRES_NEW
     * Create a new transaction, and suspend the current transaction if one exists.
     */
    @RepeatedTest(5)
    //    @Transactional // see [runTestWithMockTransactional]
    open fun testConnectionWithRequiresNew() = runTestWithMockTransactional {
        insertRandom()
        assertEquals(1, getCount())
        transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
            assertEquals(0, getCount())
            insertRandom()
            assertEquals(1, getCount())
        }
        assertEquals(2, getCount())
    }

    // TODO
    /**
     * Test for Propagation.REQUIRES_NEW with inner transaction roll-back
     * The inner transaction will be roll-back only inner transaction when the transaction marks as rollback.
     * And since isolation level is READ_COMMITTED, the inner transaction can't see the changes of outer transaction.
     */
    @RepeatedTest(5)
    fun testConnectionWithRequiresNewWithInnerTransactionRollback() = runTest {
        transactionManager.execute {
            insertRandom()
            assertEquals(1, getCount())
            transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
                insertRandom()
                assertEquals(1, getCount())
                it.setRollbackOnly()
            }
            assertEquals(1, getCount())
        }

        transactionManager.execute {
            assertEquals(1, getCount())
        }
    }

    /**
     * Test for Propagation.NEVER
     * Throw an exception cause outer transaction exists.
     */
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    open fun testPropagationNeverWithExistingTransaction() = runTestWithMockTransactional {
        assertFailsWith<IllegalTransactionStateException> {
            insertRandom()
            transactionManager.execute(TransactionDefinition.PROPAGATION_NEVER) {
                insertRandom()
            }
        }
    }

    /**
     * Test for Propagation.MANDATORY
     * Support a current transaction, throw an exception if none exists.
     */
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    open fun testPropagationMandatoryWithTransaction() = runTestWithMockTransactional {
        insertRandom()
        transactionManager.execute(TransactionDefinition.PROPAGATION_MANDATORY) {
            insertRandom()
        }
    }

    /**
     * Test for Propagation.MANDATORY
     * Throw an exception cause no transaction exists.
     */
    @RepeatedTest(5)
    open fun testPropagationMandatoryWithoutTransaction() = runTest {
        assertFailsWith<IllegalTransactionStateException> {
            transactionManager.execute(TransactionDefinition.PROPAGATION_MANDATORY) {
                insertRandom()
            }
        }
    }

    /**
     * Test for Propagation.SUPPORTS
     * Support a current transaction, execute non-transactionally if none exists.
     */
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    open fun testPropagationSupportWithTransaction() = runTestWithMockTransactional {
        insertRandom()
        transactionManager.execute(TransactionDefinition.PROPAGATION_SUPPORTS) {
            insertRandom()
        }
    }

    /**
     * Test for Propagation.SUPPORTS
     * Execute non-transactionally if none exists.
     */
    @RepeatedTest(5)
    open fun testPropagationSupportWithoutTransaction() = runTest {
        transactionManager.execute(TransactionDefinition.PROPAGATION_SUPPORTS) {
            insertRandom()
        }
        assertEquals(1, getCount())
    }

    @RepeatedTest(5)
    //    @Transactional(isolation = Isolation.READ_COMMITTED) // see [runTestWithMockTransactional]
    open fun testIsolationLevelReadUncommitted() = runTestWithMockTransactional(
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    ) {
        assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED)
        insertRandom()
        val count = getCount()
        transactionManager.execute(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW,
            TransactionDefinition.ISOLATION_READ_UNCOMMITTED
        ) {
            assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED)
            assertEquals(count, getCount())
        }
        assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED)
    }

    private suspend fun assertTransactionIsolationLevel(expected: Int) {
        val connection = TransactionManager.current().connection()
        assertEquals(expected.resolveIsolationLevel(), connection.getTransactionIsolation())
    }
}
