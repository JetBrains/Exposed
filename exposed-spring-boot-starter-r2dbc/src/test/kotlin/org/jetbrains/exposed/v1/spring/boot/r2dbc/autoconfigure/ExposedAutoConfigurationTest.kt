package org.jetbrains.exposed.v1.spring.boot.r2dbc.autoconfigure

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.spring.boot.r2dbc.Application
import org.jetbrains.exposed.v1.spring.boot.r2dbc.DatabaseInitializer
import org.jetbrains.exposed.v1.spring.reactive.transaction.ExposedSpringReactiveTransactionAttributeSource
import org.jetbrains.exposed.v1.spring.reactive.transaction.SpringReactiveTransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.test.context.event.annotation.AfterTestMethod
import org.springframework.test.context.event.annotation.BeforeTestMethod
import org.springframework.transaction.interceptor.TransactionAttributeSource

@SpringBootTest(
    classes = [Application::class, ExposedAutoConfigurationTest.CustomDatabaseConfigConfiguration::class],
    properties = ["spring.r2dbc.url=r2dbc:h2:mem:///test"]
)
open class ExposedAutoConfigurationTest {

    @Autowired(required = false)
    private var springReactiveTransactionManager: SpringReactiveTransactionManager? = null

    @Autowired(required = false)
    private var databaseInitializer: DatabaseInitializer? = null

    @Autowired
    private var databaseConfig: R2dbcDatabaseConfig.Builder? = null

    @Autowired
    private var transactionAttributeSource: TransactionAttributeSource? = null

    @OptIn(InternalApi::class)
    @BeforeTestMethod
    fun beforeTest() {
        // TODO - this should not be done, but transactions are not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()
            ?.joinToString(separator = "\n", prefix = "\n!!! ORPHAN transactions:\n") { "--> $it" }
            ?.ifEmpty { "NO transactions to clear up :)" }
            ?.also { println(it) }
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @OptIn(InternalApi::class)
    @AfterTestMethod
    fun afterTest() {
        // TODO - this should not be done, but transactions are not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()
            ?.joinToString(separator = "\n", prefix = "\n!!! ORPHAN transactions:\n") { "--> $it" }
            ?.ifEmpty { "NO transactions to clear up :)" }
            ?.also { println(it) }
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @Test
    fun `should initialize the database connection`() {
        assertNotNull(springReactiveTransactionManager)
    }

    @Test
    fun `should not create schema`() {
        assertNull(databaseInitializer)
    }

    @Test
    fun `database config can be overriden by custom one`() {
        val expectedConfig = CustomDatabaseConfigConfiguration.expectedConfig
        assertSame(databaseConfig, expectedConfig)
        assertEquals(expectedConfig.defaultMaxAttempts, databaseConfig?.defaultMaxAttempts)
        assertEquals(expectedConfig.explicitDialect, databaseConfig?.explicitDialect)
    }

    @Test
    fun testClassExcludedFromAutoConfig() {
        val contextRunner = ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(Application::class.java)
        )
        contextRunner.run { context ->
            assertThrows(NoSuchBeanDefinitionException::class.java) {
                context.getBean(R2dbcTransactionManagerAutoConfiguration::class.java)
            }
        }
    }

    @Test
    fun `load ExposedSpringReactiveTransactionAttributeSource`() {
        transactionAttributeSource?.let {
            assertEquals(ExposedSpringReactiveTransactionAttributeSource::class.java, it.javaClass)
        } ?: fail("TransactionAttributeSource bean not found")
    }

    @TestConfiguration
    open class CustomDatabaseConfigConfiguration {

        @Bean
        open fun customDatabaseConfig(): R2dbcDatabaseConfig.Builder {
            return expectedConfig
        }

        companion object {
            val expectedConfig = R2dbcDatabaseConfig {
                explicitDialect = H2Dialect()
                defaultMaxAttempts = 777
            }
        }
    }
}
