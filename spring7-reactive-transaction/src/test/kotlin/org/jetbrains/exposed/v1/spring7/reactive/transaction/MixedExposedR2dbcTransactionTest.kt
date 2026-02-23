package org.jetbrains.exposed.v1.spring7.reactive.transaction

import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MixedExposedR2dbcTransactionTest : SpringReactiveTransactionTestBase() {

    @Autowired
    private lateinit var mixedTransactionService: MixedTransactionService

    @BeforeEach
    fun setUp() = runTest {
        suspendTransaction {
            SchemaUtils.create(CustomerTable)
        }
    }

    @AfterEach
    fun tearDown() = runTest {
        suspendTransaction {
            SchemaUtils.drop(CustomerTable)
        }
    }

    @Test
    fun testSuccessfulMixedTransaction() = runTest {
        mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)

        val customers = suspendTransaction { CustomerTable.selectAll().toList() }

        assertEquals(2, customers.size)
    }

    @Test
    fun testFailedMixedTransaction() = runTest {
        assertFailsWith<RuntimeException> {
            mixedTransactionService.saveTwoThingsSpringTransactional(fail = true)
        }

        val customers = suspendTransaction { CustomerTable.selectAll().toList() }

        assertEquals(0, customers.size)
    }

    @Test
    fun testSuccessfulRequiresNewTransactions() = runTest {
        mixedTransactionService.withNewTransaction {
            mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            mixedTransactionService.withNewTransaction {
                mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            }
        }

        val customers = suspendTransaction { CustomerTable.selectAll().toList() }

        assertEquals(4, customers.size)
    }

    @Test
    fun testFailedRequiresNewTransactions() = runTest {
        mixedTransactionService.withNewTransaction {
            mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            assertFailsWith<RuntimeException> {
                mixedTransactionService.withNewTransaction {
                    mixedTransactionService.saveTwoThingsSpringTransactional(fail = true)
                }
            }
        }

        val customers = suspendTransaction { CustomerTable.selectAll().toList() }

        assertEquals(2, customers.size)
    }

    @Test
    fun testSuccessfulNestedTransactions() = runTest {
        mixedTransactionService.withNewTransaction {
            mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            mixedTransactionService.withNestedTransaction {
                mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            }
        }

        val customers = suspendTransaction { CustomerTable.selectAll().toList() }

        assertEquals(4, customers.size)
    }

    @Test
    fun testFailedNestedTransactions() = runTest {
        mixedTransactionService.withNewTransaction {
            mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            assertFailsWith<RuntimeException> {
                mixedTransactionService.withNestedTransaction {
                    mixedTransactionService.saveTwoThingsSpringTransactional(fail = true)
                }
            }
        }

        val customers = suspendTransaction { CustomerTable.selectAll().toList() }

        assertEquals(2, customers.size)
    }
}

@Service
open class MixedTransactionService {

    @Autowired
    private lateinit var connectionFactory: ConnectionFactory

    private val client: DatabaseClient by lazy { DatabaseClient.create(connectionFactory) }
    private var nextNameIndex: Int = 0

    @Transactional
    open suspend fun saveTwoThingsSpringTransactional(fail: Boolean) {
        saveTwoThings(fail)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open suspend fun <T> withNewTransaction(block: suspend () -> T): T {
        return block()
    }

    @Transactional(propagation = Propagation.NESTED)
    open suspend fun <T> withNestedTransaction(block: suspend () -> T): T {
        return block()
    }

    private suspend fun saveTwoThings(fail: Boolean) {
        CustomerTable.insert {
            it[id] = UUID.randomUUID()
            it[name] = "Test${nextNameIndex++}"
        }
        client
            .sql("INSERT INTO customer VALUES (:id, :name)")
            .bind("id", UUID.randomUUID())
            .bind("name", "Test${nextNameIndex++}")
            .fetch()
            .rowsUpdated()

        @Suppress("UseCheckOrError")
        if (fail) {
            throw IllegalStateException("Fail")
        }
    }
}

// originally should be in SpringTransactionEntityTest (but this does not exist for R2DBC)
object CustomerTable : UUIDTable(name = "customer") {
    val name = varchar(name = "name", length = 255).uniqueIndex()
}
