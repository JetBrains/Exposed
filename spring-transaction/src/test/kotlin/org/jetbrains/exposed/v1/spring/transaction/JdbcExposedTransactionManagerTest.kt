package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.annotation.Commit
import org.springframework.test.annotation.Repeat
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.util.Random
import javax.sql.DataSource
import kotlin.test.assertFailsWith

/**
 * Similar to [ExposedTransactionManagerTest] but working with Spring JDBC
 * constructs like [JdbcClient] instead of Exposed APIs to verify that it
 * works like expected.
 */
open class JdbcExposedTransactionManagerTest : SpringTransactionTestBase() {

    object T1 : Table() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    @Autowired
    private lateinit var dataSource: DataSource

    private val jdbc: JdbcClient by lazy { JdbcClient.create(dataSource) }

    private fun insertRandom() {
        jdbc.sql(
            "INSERT INTO ${T1.tableName} VALUES (:value)"
        ).param(
            "value", Random().nextInt().toString()
        ).update()
    }

    private fun insert(value: String) {
        jdbc.sql(
            "INSERT INTO ${T1.tableName} VALUES (:value)"
        ).param(
            "value", value
        ).update()
    }

    private fun getCount(): Int = jdbc
        .sql("SELECT count(*) FROM ${T1.tableName}")
        .query<Int>(Int::class.java).single()

    private fun getSingleValue(): String = jdbc
        .sql("SELECT * FROM ${T1.tableName}")
        .query().singleRow()["c1"] as String

    @BeforeEach
    fun beforeTest() {
        transactionManager.execute {
            SchemaUtils.create(T1)
        }
    }

    @Test
    @Transactional
    @Commit
    @Repeat(5)
    open fun testConnection() {
        insertRandom()
        assertEquals(1, getCount())
    }

    @Test
    @Transactional
    @Commit
    @Repeat(5)
    open fun testConnection2() {
        val rnd = Random().nextInt().toString()
        insert(rnd)
        assertEquals(rnd, getSingleValue())
    }

    @Test
    @Repeat(5)
    @Commit
    open fun testConnectionCombineWithExposedTransaction() {
        transaction {
            val rnd = Random().nextInt().toString()
            insert(rnd)
            assertEquals(rnd, getSingleValue())

            this@JdbcExposedTransactionManagerTest.transactionManager.execute {
                insertRandom()
                assertEquals(2, getCount())
            }
        }
    }

    @Test
    @Repeat(5)
    @Commit
    @Transactional
    open fun testConnectionCombineWithExposedTransaction2() {
        val rnd = Random().nextInt().toString()
        insert(rnd)
        assertEquals(rnd, getSingleValue())

        transaction {
            insertRandom()
            assertEquals(2, getCount())
        }
    }

    /**
     * Test for Propagation.NESTED
     * Execute within a nested transaction if a current transaction exists, behave like REQUIRED otherwise.
     */
    @Test
    @Repeat(5)
    @Transactional
    open fun testConnectionWithNestedTransactionCommit() {
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
    @Test
    @Repeat(5)
    @Transactional
    open fun testConnectionWithNestedTransactionInnerRollback() {
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
    @Test
    @Repeat(5)
    fun testConnectionWithNestedTransactionOuterRollback() {
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

    /**
     * Test for Propagation.REQUIRES_NEW
     * Create a new transaction, and suspend the current transaction if one exists.
     */
    @Test
    @Repeat(5)
    @Transactional
    open fun testConnectionWithRequiresNew() {
        insertRandom()
        assertEquals(1, getCount())
        transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
            assertEquals(0, getCount())
            insertRandom()
            assertEquals(1, getCount())
        }
        assertEquals(2, getCount())
    }

    /**
     * Test for Propagation.REQUIRES_NEW with inner transaction roll-back
     * The inner transaction will be roll-back only inner transaction when the transaction marks as rollback.
     * And since isolation level is READ_COMMITTED, the inner transaction can't see the changes of outer transaction.
     */
    @Test
    @Repeat(5)
    fun testConnectionWithRequiresNewWithInnerTransactionRollback() {
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
    @Test
    @Repeat(5)
    @Transactional
    open fun testPropagationNeverWithExistingTransaction() {
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
    @Test
    @Repeat(5)
    @Transactional
    open fun testPropagationMandatoryWithTransaction() {
        insertRandom()
        transactionManager.execute(TransactionDefinition.PROPAGATION_MANDATORY) {
            insertRandom()
        }
    }

    /**
     * Test for Propagation.MANDATORY
     * Throw an exception cause no transaction exists.
     */
    @Test
    @Repeat(5)
    open fun testPropagationMandatoryWithoutTransaction() {
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
    @Test
    @Repeat(5)
    @Transactional
    open fun testPropagationSupportWithTransaction() {
        insertRandom()
        transactionManager.execute(TransactionDefinition.PROPAGATION_SUPPORTS) {
            insertRandom()
        }
    }

    /**
     * Test for Propagation.SUPPORTS
     * Execute non-transactionally if none exists.
     */
    @Test
    @Repeat(5)
    open fun testPropagationSupportWithoutTransaction() {
        transactionManager.execute(TransactionDefinition.PROPAGATION_SUPPORTS) {
            insertRandom()
        }
        assertEquals(1, getCount())
    }

    /**
     * Test for Isolation Level
     */
    @Test
    @Repeat(5)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    open fun testIsolationLevelReadUncommitted() {
        assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED)
        insertRandom()
        val count = getCount()
        transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW, TransactionDefinition.ISOLATION_READ_UNCOMMITTED) {
            assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED)
            assertEquals(count, getCount())
        }
        assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED)
    }

    @AfterEach
    fun afterTest() {
        transactionManager.execute {
            SchemaUtils.drop(T1)
        }
    }

    private fun assertTransactionIsolationLevel(expected: Int) {
        val connection = TransactionManager.current().connection
        assertEquals(expected, connection.transactionIsolation)
    }
}
