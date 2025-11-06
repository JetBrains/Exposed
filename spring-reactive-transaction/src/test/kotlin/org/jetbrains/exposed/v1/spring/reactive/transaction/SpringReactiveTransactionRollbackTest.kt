package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.junit.Test
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

    @BeforeTest
    fun beforeTest() = runTest {
        val testRollback = container.getBean(TestRollback::class.java)
        testRollback.init()
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

    @AfterTest
    fun afterTest() {
        container.close()
    }
}

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
open class TransactionManagerAttributeSourceTestConfig {

    @Bean
    open fun cxFactory(): ConnectionFactory = ConnectionFactories.get(TestDB.H2_V2.connection.invoke())

    @Bean
    open fun transactionManager(connectionFactory: ConnectionFactory) = SpringReactiveTransactionManager(connectionFactory)

    @Bean
    open fun transactionAttributeSource() = ExposedSpringTransactionAttributeSource()

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
