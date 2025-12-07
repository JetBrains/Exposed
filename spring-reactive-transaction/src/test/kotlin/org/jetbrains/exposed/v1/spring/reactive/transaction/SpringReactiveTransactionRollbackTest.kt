package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpringReactiveTransactionRollbackTest {

    val container = AnnotationConfigApplicationContext(TransactionManagerAttributeSourceTestConfig::class.java)

    @OptIn(InternalApi::class)
    @BeforeTest
    fun beforeTest() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)
        testRollback.init()

        // TODO - this should not be done, but transaction is not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()
            ?.joinToString(separator = "\n", prefix = "\n!!! ORPHAN transactions:\n") { "--> $it" }
            ?.ifEmpty { "NO transactions to clear up :)" }
            ?.also { println(it) }
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
        println("\n-----------STARTING TEST-----------")
    }

    @OptIn(InternalApi::class)
    @AfterTest
    fun afterTest() {
        println("\n-----------FINISHED TEST-----------")
        container.close()

        // TODO - this should not be done, but transaction is not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()
            ?.joinToString(separator = "\n", prefix = "\n!!! ORPHAN transactions:\n") { "--> $it" }
            ?.ifEmpty { "NO transactions to clear up :)" }
            ?.also { println(it) }
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @Test
    fun `test ExposedR2dbcException rollback`() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<ExposedR2dbcException> {
            testRollback.suspendTransaction {
                insertOriginTable()
                insertWrongTable("1234567890")
            }
        }

        assertEquals(0, testRollback.entireTableSize())
    }

    @Test
    fun `test RuntimeException rollback`() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<RuntimeException> {
            testRollback.suspendTransaction {
                insertOriginTable()
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
                insertOriginTable()
                @Suppress("TooGenericExceptionThrown")
                throw Exception()
            }
        }

        assertEquals(1, testRollback.entireTableSize())
    }
}

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
open class TransactionManagerAttributeSourceTestConfig {

    @Bean
    open fun cxFactory(): ConnectionFactory = ConnectionFactories.get(TestDB.H2_V2.connection.invoke())

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

    open suspend fun insertOriginTable() {
        RollbackTable.insert {
            it[name] = "1"
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
}

object RollbackTable : LongIdTable("test_rollback") {
    val name = varchar("name", 5)
}

object WrongDefinedRollbackTable : LongIdTable("test_rollback") {
    val name = varchar("name", 10)
}
