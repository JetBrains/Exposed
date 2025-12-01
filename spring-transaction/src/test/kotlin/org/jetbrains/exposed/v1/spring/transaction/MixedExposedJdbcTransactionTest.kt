package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MixedExposedJdbcTransactionTest : SpringTransactionTestBase() {

    @Autowired
    private lateinit var mixedTransactionService: MixedTransactionService

    @BeforeTest
    fun setUp() {
        transaction {
            SchemaUtils.create(CustomerTable)
        }
    }

    @Test
    fun testSuccessfulMixedTransaction() {
        mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)

        val customers = transaction { CustomerTable.selectAll().toList() }

        assertEquals(2, customers.size)
    }

    @Test
    fun testFailedMixedTransaction() {
        assertThrows(RuntimeException::class.java) {
            mixedTransactionService.saveTwoThingsSpringTransactional(fail = true)
        }

        val customers = transaction { CustomerTable.selectAll().toList() }

        assertEquals(0, customers.size)
    }

    @Test
    fun testSuccessfulRequiresNewTransactions() {
        mixedTransactionService.withNewTransaction {
            mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            mixedTransactionService.withNewTransaction {
                mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            }
        }

        val customers = transaction { CustomerTable.selectAll().toList() }

        assertEquals(4, customers.size)
    }

    @Test
    fun testFailedRequiresNewTransactions() {
        mixedTransactionService.withNewTransaction {
            mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            assertThrows(RuntimeException::class.java) {
                mixedTransactionService.withNewTransaction {
                    mixedTransactionService.saveTwoThingsSpringTransactional(fail = true)
                }
            }
        }

        val customers = transaction { CustomerTable.selectAll().toList() }

        assertEquals(2, customers.size)
    }

    @Test
    fun testSuccessfulNestedTransactions() {
        mixedTransactionService.withNewTransaction {
            mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            mixedTransactionService.withNestedTransaction {
                mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            }
        }

        val customers = transaction { CustomerTable.selectAll().toList() }

        assertEquals(4, customers.size)
    }

    @Test
    fun testFailedNestedTransactions() {
        mixedTransactionService.withNewTransaction {
            mixedTransactionService.saveTwoThingsSpringTransactional(fail = false)
            assertThrows(RuntimeException::class.java) {
                mixedTransactionService.withNestedTransaction {
                    mixedTransactionService.saveTwoThingsSpringTransactional(fail = true)
                }
            }
        }

        val customers = transaction { CustomerTable.selectAll().toList() }

        assertEquals(2, customers.size)
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(CustomerTable)
        }
    }
}

@Service
open class MixedTransactionService {

    @Autowired
    private lateinit var dataSource: DataSource

    private val client: JdbcClient by lazy { JdbcClient.create(dataSource) }
    private var nextNameIndex: Int = 0

    @Transactional
    open fun saveTwoThingsSpringTransactional(fail: Boolean) {
        saveTwoThings(fail)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun <T> withNewTransaction(block: () -> T): T {
        return block()
    }

    @Transactional(propagation = Propagation.NESTED)
    open fun <T> withNestedTransaction(block: () -> T): T {
        return block()
    }

    private fun saveTwoThings(fail: Boolean) {
        CustomerTable.insert {
            it[id] = UUID.randomUUID()
            it[name] = "Test${nextNameIndex++}"
        }
        client
            .sql("INSERT INTO customer VALUES (:id, :name)")
            .param("id", UUID.randomUUID())
            .param("name", "Test${nextNameIndex++}")
            .update()

        @Suppress("UseCheckOrError")
        if (fail) {
            throw IllegalStateException("Fail")
        }
    }
}
