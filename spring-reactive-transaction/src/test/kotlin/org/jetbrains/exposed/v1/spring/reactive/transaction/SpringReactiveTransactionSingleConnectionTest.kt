package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.junit.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.connection.ConnectionFactoryUtils
import org.springframework.r2dbc.connection.SingleConnectionFactory
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class SpringReactiveTransactionSingleConnectionTest {
    object T1 : Table() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    val singleConnectionH2TestContainer = AnnotationConfigApplicationContext(SingleConnectionH2TestConfig::class.java)
    val transactionManager: ReactiveTransactionManager = singleConnectionH2TestContainer.getBean(ReactiveTransactionManager::class.java)
    val connectionFactory: ConnectionFactory = singleConnectionH2TestContainer.getBean(ConnectionFactory::class.java)

    @OptIn(InternalApi::class)
    @BeforeTest
    fun beforeTest() = runTest {
        transactionManager.execute {
            SchemaUtils.create(T1)
        }
        // TODO - this should not be done, but transaction is not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @OptIn(InternalApi::class)
    @AfterTest
    fun afterTest() = runTest {
        transactionManager.execute {
            SchemaUtils.drop(T1)
        }
        // TODO - this should not be done, but transaction is not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
        singleConnectionH2TestContainer.close()
    }

    @Test
    fun `start transaction with non default isolation level`() = runTest {
        transactionManager.execute(
            isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE,
        ) {
            T1.selectAll().toList()
        }
    }

    @Test
    fun `nested transaction with non default isolation level`() = runTest {
        transactionManager.execute(
            isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE,
        ) {
            T1.selectAll().toList()

            // Nested transaction will inherit isolation level from parent transaction because it uses the same connection
            transactionManager.execute(
                isolationLevel = TransactionDefinition.ISOLATION_READ_UNCOMMITTED,
            ) {
                val cx = ConnectionFactoryUtils.getConnection(connectionFactory).awaitFirst()
                assertEquals(
                    cx.transactionIsolationLevel,
                    TransactionDefinition.ISOLATION_SERIALIZABLE.resolveIsolationLevel()
                )
                cx.close().awaitFirstOrNull()

                T1.selectAll().toList()
            }
            T1.selectAll().toList()
        }
    }
}

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
open class SingleConnectionH2TestConfig {

    @Bean
    open fun singleConnectionH2Factory(): ConnectionFactory {
        // args -> SingleConnectionFactory(url, suppressClose)
        return SingleConnectionFactory(TestDB.H2_V2.connection.invoke(), true)
    }

    @Bean
    open fun singleConnectionH2TransactionManager(
        @Qualifier("singleConnectionH2Factory") connectionFactory: ConnectionFactory
    ): ReactiveTransactionManager = SpringReactiveTransactionManager(
        connectionFactory,
        R2dbcDatabaseConfig {
            useNestedTransactions = true
            explicitDialect = H2Dialect()
        }
    )
}
