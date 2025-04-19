package org.jetbrains.exposed.spring

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.UnexpectedRollbackException
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @author ivan@daangn.com
 * @author zbqmgldjfh@gmail.com
 */
class SpringTransactionRollbackTest {

    val container = AnnotationConfigApplicationContext(TransactionManagerAttributeSourceTestConfig::class.java)

    @BeforeTest
    fun beforeTest() {
        val testRollback = container.getBean(TestRollback::class.java)
        testRollback.init()
    }

    @Test
    fun `test ExposedSQLException rollback`() {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<ExposedSQLException> {
            testRollback.transaction {
                insertOriginTable("1")
                insertWrongTable("12345678901234567890")
            }
        }

        assertEquals(0, testRollback.entireTableSize())
    }

    @Test
    fun `test RuntimeException rollback`() {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<RuntimeException> {
            testRollback.transaction {
                insertOriginTable("1")
                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException()
            }
        }

        assertEquals(0, testRollback.entireTableSize())
    }

    @Test
    fun `test check exception commit`() {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<Exception> {
            testRollback.transaction {
                insertOriginTable("1")
                @Suppress("TooGenericExceptionThrown")
                throw Exception()
            }
        }

        assertEquals(1, testRollback.entireTableSize())
    }

    @Test
    fun `exception in inner Tx causes rollback of outer Tx`() {
        val testRollback = container.getBean(TestRollback::class.java)

        assertFailsWith<UnexpectedRollbackException> {
            testRollback.transaction { // outer logicTx start
                testRollback.insertOriginTable("Tx1")

                try {
                    testRollback.transaction { // inner logicTx start
                        testRollback.insertOriginTable("Tx2")

                        @Suppress("TooGenericExceptionThrown")
                        throw RuntimeException()
                        // isGlobalRollbackOnParticipationFailure == true -> doSetRollbackOnly() -> mark as globalRollBack
                    }
                } catch (@Suppress("SwallowedException") e: RuntimeException) {
                    // something...
                }
            } // when outer logicTx commit() -> check globalRollBack mark -> throw UnexpectedRollbackException -> rollback
        }

        assertEquals(0, testRollback.entireTableSize())
    }

    @Test
    fun `isRollback is managed separately for different transactions`() {
        val testRollback = container.getBean(TestRollback::class.java)

        assertFailsWith<UnexpectedRollbackException> {
            testRollback.transaction { // outer logicTx start
                val outerTx = TransactionManager.currentOrNull()
                assertFalse(outerTx!!.isMarkedRollback())

                try {
                    testRollback.transaction { // inner logicTx start
                        val innerTx = TransactionManager.currentOrNull()
                        assertFalse(innerTx!!.isMarkedRollback())

                        @Suppress("TooGenericExceptionThrown")
                        throw RuntimeException() // mark as globalRollBack
                    }
                } catch (@Suppress("SwallowedException") e: RuntimeException) {
                    // something...
                }

                assertTrue(outerTx.isMarkedRollback())

                testRollback.transactionWithRequiresNew { // separate transaction
                    val innerTx = TransactionManager.currentOrNull()
                    assertFalse(innerTx!!.isMarkedRollback())
                }

                testRollback.transaction {
                    val innerTx = TransactionManager.currentOrNull()
                    assertTrue(innerTx!!.isMarkedRollback())
                }
            }
        }

        testRollback.transaction { // other transaction not affected by previous transaction rollback status
            val newTx = TransactionManager.currentOrNull()
            assertFalse(newTx!!.isMarkedRollback())
        }
    }

    @Test
    fun `requiresNew should rollback innerTx without affecting outerTx`() {
        val testRollback = container.getBean(TestRollback::class.java)

        testRollback.transaction {
            testRollback.insertOriginTable("Tx1")

            try {
                testRollback.transactionWithRequiresNew {
                    testRollback.insertOriginTable("Tx2")

                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException()
                }
            } catch (@Suppress("SwallowedException") e: RuntimeException) {
                // something...
            }
        }

        val entities = testRollback.selectAll()
        assertEquals(1, entities.size)
        assertEquals("Tx1", entities.first().name)
    }

    @Test
    fun `supports should participate in existing transaction but not rollback when none exists`() {
        val testRollback = container.getBean(TestRollback::class.java)

        assertFailsWith<RuntimeException> {
            // Execute without a transaction -> Should not be rolled back
            testRollback.transactionWithSupports {
                testRollback.insertOriginTable("No Tx")

                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException() // Non-transactional, so it should not be rolled back
            }
        }

        assertEquals(1, testRollback.entireTableSize()) // Data should remain

        // Execute within a transaction -> Should be rolled back
        assertFailsWith<RuntimeException> {
            testRollback.transaction {
                testRollback.insertOriginTable("With Tx")

                testRollback.transactionWithSupports {
                    testRollback.insertOriginTable("Supports Tx")

                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException() // Should trigger rollback
                }
            }
        }

        val entities = testRollback.selectAll()
        assertEquals(1, entities.size) // Only the first case's data should remain
        assertEquals("No Tx", entities.first().name)
    }

    @Test
    fun `notSupported should suspend outer transaction and execute without transaction`() {
        val testRollback = container.getBean(TestRollback::class.java)

        testRollback.transaction {
            testRollback.insertOriginTable("Outer Tx")

            try {
                testRollback.transactionWithNotSupported {
                    testRollback.insertOriginTable("No Tx")

                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException() // Since it's non-transactional, it won't be rolled back
                }
            } catch (@Suppress("SwallowedException") e: RuntimeException) {
                // Ignore exception
            }
        }

        assertEquals(2, testRollback.entireTableSize()) // Both records should remain
    }

    @Test
    fun `mandatory should fail if no existing transaction but participate if one exists`() {
        val testRollback = container.getBean(TestRollback::class.java)

        assertFailsWith<IllegalTransactionStateException> {
            testRollback.transactionWithMandatory {
                testRollback.insertOriginTable("No Parent Tx")
            }
        }

        testRollback.transaction {
            testRollback.insertOriginTable("Parent Tx")
            testRollback.transactionWithMandatory {
                testRollback.insertOriginTable("Mandatory Tx")
            }
        }

        assertEquals(2, testRollback.entireTableSize()) // Both records should remain
    }

    @Test
    fun `nested should rollback innerTx without affecting outerTx`() {
        val testRollback = container.getBean(TestRollback::class.java)

        testRollback.transaction {
            testRollback.insertOriginTable("Tx1")

            try {
                testRollback.transactionWithNested {
                    testRollback.insertOriginTable("Tx2")

                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException() // Rollback only the inner transaction
                }
            } catch (@Suppress("SwallowedException") e: RuntimeException) {
                // something...
            }
        }

        val entities = testRollback.selectAll()
        assertEquals(1, entities.size)
        assertEquals("Tx1", entities.first().name) // Only the outer transaction should remain
    }

    @AfterTest
    fun afterTest() {
        container.close()
    }
}

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
open class TransactionManagerAttributeSourceTestConfig {

    @Bean
    open fun dataSource(): EmbeddedDatabase = EmbeddedDatabaseBuilder().setName("embeddedTest1").setType(
        EmbeddedDatabaseType.H2
    ).build()

    @Bean
    open fun transactionManager(dataSource: DataSource) = SpringTransactionManager(dataSource)

    @Bean
    open fun transactionAttributeSource() = ExposedSpringTransactionAttributeSource()

    @Bean
    open fun testRollback() = TestRollback()
}

@Transactional
open class TestRollback {

    open fun init() {
        SchemaUtils.create(RollbackTable)
        RollbackTable.deleteAll()
    }

    open fun transaction(block: TestRollback.() -> Unit) {
        block()
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun transactionWithRequiresNew(block: TestRollback.() -> Unit) {
        block()
    }

    @Transactional(propagation = Propagation.NESTED)
    open fun transactionWithNested(block: TestRollback.() -> Unit) {
        block()
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    open fun transactionWithSupports(block: TestRollback.() -> Unit) {
        block()
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    open fun transactionWithNotSupported(block: TestRollback.() -> Unit) {
        block()
    }

    @Transactional(propagation = Propagation.MANDATORY)
    open fun transactionWithMandatory(block: TestRollback.() -> Unit) {
        block()
    }

    open fun insertOriginTable(name: String) {
        RollbackTable.insert {
            it[RollbackTable.name] = name
        }
    }

    open fun insertWrongTable(name: String) {
        WrongDefinedRollbackTable.insert {
            it[WrongDefinedRollbackTable.name] = name
        }
    }

    open fun entireTableSize(): Long {
        return RollbackTable.selectAll().count()
    }

    open fun selectAll(): List<RollbackEntity> {
        return RollbackTable.selectAll().map {
            RollbackEntity(
                id = it[RollbackTable.id],
                name = it[RollbackTable.name]
            )
        }
    }
}

object RollbackTable : LongIdTable("test_rollback") {
    val name = varchar("name", 15)
}

object WrongDefinedRollbackTable : LongIdTable("test_rollback") {
    val name = varchar("name", 20)
}

data class RollbackEntity(val id: EntityID<Long>, val name: String)
