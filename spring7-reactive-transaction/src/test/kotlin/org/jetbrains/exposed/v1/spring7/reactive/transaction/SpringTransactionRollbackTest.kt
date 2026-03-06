package org.jetbrains.exposed.v1.spring7.reactive.transaction

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.UnexpectedRollbackException
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpringTransactionRollbackTest {

    val container = AnnotationConfigApplicationContext(TransactionManagerAttributeSourceTestConfig::class.java)

    @BeforeEach
    fun beforeTest() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)
        testRollback.init()
    }

    @AfterEach
    fun afterTest() {
        container.close()
    }

    @Test
    fun `test ExposedR2dbcException rollback`() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<IllegalArgumentException> {
            testRollback.suspendTransaction {
                insertOriginTable("1")
                insertWrongTable("12345678901234567890")
            }
        }

        assertEquals(0, testRollback.entireTableSize())
    }

    @Test
    fun `test RuntimeException rollback`() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<RuntimeException> {
            testRollback.suspendTransaction {
                insertOriginTable("1")
                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException()
            }
        }

        assertEquals(0, testRollback.entireTableSize())
    }

    @Test
    fun `test check exception commit`() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<Exception> {
            testRollback.suspendTransaction {
                insertOriginTable("1")
                @Suppress("TooGenericExceptionThrown")
                throw Exception()
            }
        }

        assertEquals(1, testRollback.entireTableSize())
    }

    @Test
    fun `exception in inner Tx causes rollback of outer Tx`() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)

        assertFailsWith<UnexpectedRollbackException> {
            testRollback.suspendTransaction { // outer logicTx start
                testRollback.insertOriginTable("Tx1")

                try {
                    testRollback.suspendTransaction { // inner logicTx start
                        testRollback.insertOriginTable("Tx2")

                        @Suppress("TooGenericExceptionThrown")
                        throw RuntimeException()
                        // isGlobalRollbackOnParticipationFailure == true -> doSetRollbackOnly() -> mark as globalRollBack
                    }
                } catch (@Suppress("SwallowedException") _: RuntimeException) {
                    // Ignore exception
                }
            } // when outer logicTx commit() -> check globalRollBack mark -> throw UnexpectedRollbackException -> rollback
        }

        assertEquals(0, testRollback.entireTableSize())
    }

    @Test
    fun `isRollback is managed separately for different transactions`() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)

        assertFailsWith<UnexpectedRollbackException> {
            testRollback.suspendTransaction { // outer logicTx start
                val outerTx = TransactionManager.currentOrNull()
                assertNotNull(outerTx)
                assertFalse(outerTx.isMarkedRollback())

                try {
                    testRollback.suspendTransaction { // inner logicTx start; inner == outer
                        val innerTx = TransactionManager.currentOrNull()
                        assertNotNull(innerTx)
                        assertFalse(innerTx.isMarkedRollback())

                        @Suppress("TooGenericExceptionThrown")
                        throw RuntimeException() // mark as globalRollBack
                    }
                } catch (@Suppress("SwallowedException") _: RuntimeException) {
                    // Ignore exception
                }

                assertTrue(outerTx.isMarkedRollback())

                testRollback.transactionWithRequiresNew { // separate transaction != outer
                    val newInnerTx = TransactionManager.currentOrNull()
                    assertNotNull(newInnerTx)
                    assertFalse(newInnerTx.isMarkedRollback())
                }

                testRollback.suspendTransaction { // inner == outer, so still marked for rollback
                    val innerTx = TransactionManager.currentOrNull()
                    assertNotNull(innerTx)
                    assertTrue(innerTx.isMarkedRollback())
                }
            }
        }

        testRollback.suspendTransaction { // new outer transaction not affected by previous transaction rollback status
            val newTx = TransactionManager.currentOrNull()
            assertNotNull(newTx)
            assertFalse(newTx.isMarkedRollback())
        }
    }

    @Test
    fun `requiresNew should rollback innerTx without affecting outerTx`() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)

        testRollback.suspendTransaction { // outer logicTx start
            testRollback.insertOriginTable("Tx1")

            try {
                testRollback.transactionWithRequiresNew { // outer != this
                    testRollback.insertOriginTable("Tx2")

                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException()
                }
            } catch (@Suppress("SwallowedException") _: RuntimeException) {
                // Ignore exception
            }
        }

        val entities = testRollback.selectAll()
        assertEquals(1, entities.size)
        assertEquals("Tx1", entities.first().name)
    }

    @Test
    fun `supports should participate in existing transaction but not rollback when none exists`() = runTest {
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
            testRollback.suspendTransaction {
                testRollback.insertOriginTable("With Tx")

                testRollback.transactionWithSupports { // outer == this
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
    fun `notSupported should suspend outer transaction and execute without transaction`() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)

        testRollback.suspendTransaction {
            testRollback.insertOriginTable("Tx1")

            try {
                testRollback.transactionWithNotSupported {
                    testRollback.insertOriginTable("No Tx")

                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException() // Since it's non-transactional, it won't be rolled back
                }
            } catch (@Suppress("SwallowedException") _: RuntimeException) {
                // Ignore exception
            }
        }

        assertEquals(2, testRollback.entireTableSize()) // Both records should remain
    }

    @Test
    fun `mandatory should fail if no existing transaction but participate if one exists`() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)

        assertFailsWith<IllegalTransactionStateException> {
            testRollback.transactionWithMandatory {
                testRollback.insertOriginTable("No Parent Tx") // Will trigger roll back
            }
        }

        testRollback.suspendTransaction {
            testRollback.insertOriginTable("Tx 1")
            testRollback.transactionWithMandatory { // outer == this
                testRollback.insertOriginTable("Tx 2")
            }
        }

        assertEquals(2, testRollback.entireTableSize()) // Both records should remain

        assertFailsWith<UnexpectedRollbackException> {
            testRollback.suspendTransaction {
                testRollback.insertOriginTable("Tx11")

                try {
                    testRollback.transactionWithMandatory { // outer == this
                        testRollback.insertOriginTable("Tx22")

                        @Suppress("TooGenericExceptionThrown")
                        throw RuntimeException()
                    }
                } catch (@Suppress("SwallowedException") _: RuntimeException) {
                    // Ignore exception
                }
            }
        }

        val entities = testRollback.selectAll()
        assertEquals(2, entities.size) // Only original records should remain
        assertTrue { entities.none { it.name.startsWith("No ") || it.name.startsWith("New ") } }
    }
}

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
open class TransactionManagerAttributeSourceTestConfig {

    @Bean
    open fun cxFactory(): ConnectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///embeddedTest1;DB_CLOSE_DELAY=-1;")

    @Bean
    open fun transactionManager(connectionFactory: ConnectionFactory) = SpringReactiveTransactionManager(
        connectionFactory,
        R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
    )

    @Bean
    open fun transactionAttributeSource() = ExposedSpringReactiveTransactionAttributeSource()

    @Bean
    open fun testRollback() = TestRollback()
}

@Transactional
open class TestRollback {

    open suspend fun init() {
        SchemaUtils.create(RollbackTable)
        RollbackTable.deleteAll()
    }

    open suspend fun suspendTransaction(block: suspend TestRollback.() -> Unit) {
        block()
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open suspend fun transactionWithRequiresNew(block: suspend TestRollback.() -> Unit) {
        block()
    }

    @Transactional(propagation = Propagation.NESTED)
    open suspend fun transactionWithNested(block: suspend TestRollback.() -> Unit) {
        block()
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    open suspend fun transactionWithSupports(block: suspend TestRollback.() -> Unit) {
        block()
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    open suspend fun transactionWithNotSupported(block: suspend TestRollback.() -> Unit) {
        block()
    }

    @Transactional(propagation = Propagation.MANDATORY)
    open suspend fun transactionWithMandatory(block: suspend TestRollback.() -> Unit) {
        block()
    }

    open suspend fun insertOriginTable(name: String) {
        RollbackTable.insert {
            it[RollbackTable.name] = name
        }
    }

    open suspend fun insertWrongTable(name: String) {
        WrongDefinedRollbackTable.insert {
            it[WrongDefinedRollbackTable.name] = name
        }
    }

    open suspend fun entireTableSize(): Long {
        return RollbackTable.selectAll().count()
    }

    open suspend fun selectAll(): List<RollbackEntity> {
        return RollbackTable.selectAll().map {
            RollbackEntity(
                id = it[RollbackTable.id],
                name = it[RollbackTable.name]
            )
        }.toList()
    }
}

object RollbackTable : LongIdTable("test_rollback") {
    val name = varchar("name", 5)
}

object WrongDefinedRollbackTable : LongIdTable("test_rollback") {
    val name = varchar("name", 10)
}

data class RollbackEntity(val id: EntityID<Long>, val name: String)
