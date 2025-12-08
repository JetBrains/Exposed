package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.TransactionManagementConfigurer
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import org.springframework.transaction.support.DefaultTransactionDefinition

@Configuration
@EnableTransactionManagement
/*(mode = AdviceMode.ASPECTJ, proxyTargetClass = true)*/
open class TestConfig : TransactionManagementConfigurer {

    @Bean
    open fun cxFactory(): ConnectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///embeddedTest;DB_CLOSE_DELAY=-1;")

    @Bean
    override fun annotationDrivenTransactionManager(): SpringReactiveTransactionManager = SpringReactiveTransactionManager(
        cxFactory(),
        R2dbcDatabaseConfig {
            useNestedTransactions = true
            explicitDialect = H2Dialect()
        }
    )
}

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [TestConfig::class])
@TestMethodOrder(MethodOrderer.MethodName::class)
@Suppress("UnnecessaryAbstractClass")
abstract class SpringReactiveTransactionTestBase {

    @Autowired
    lateinit var ctx: ApplicationContext

    @Autowired
    lateinit var transactionManager: ReactiveTransactionManager

    /**
     * Invokes [runTest] with the [testBody] executed by a [TransactionalOperator] that is set up to follow the same
     * rollback rules as `@Transactional`.
     *
     * Currently, `@Transactional` in Spring's `TestContext` is only configured to find a `PlatformTransactionManager`,
     * so it is completely unusable for Spring-R2dbc unit tests.
     *
     * [Open Issue](https://github.com/spring-projects/spring-framework/issues/24226)
     */
    fun runTestWithMockTransactional(
        propagationBehavior: Int = TransactionDefinition.PROPAGATION_REQUIRED,
        isolationLevel: Int = TransactionDefinition.ISOLATION_DEFAULT,
        testBody: suspend TestScope.(ReactiveTransaction) -> Unit
    ) {
        if (transactionManager !is SpringReactiveTransactionManager) error("Wrong txManager instance: ${this.javaClass.name}")

        val trxDef = DefaultTransactionDefinition(propagationBehavior).apply {
            this.isolationLevel = isolationLevel
        }

        runTest {
            val trxOp = TransactionalOperator.create(transactionManager, trxDef)
            trxOp.executeAndAwait {
                testBody(it)
                it.setRollbackOnly()
            }
        }
    }
}

suspend fun ReactiveTransactionManager.execute(
    propagationBehavior: Int = TransactionDefinition.PROPAGATION_REQUIRED,
    isolationLevel: Int = TransactionDefinition.ISOLATION_DEFAULT,
    readOnly: Boolean = false,
    timeout: Int? = null,
    block: suspend (ReactiveTransaction) -> Unit
) {
    if (this !is SpringReactiveTransactionManager) error("Wrong txManager instance: ${this.javaClass.name}")
    val trxDef = DefaultTransactionDefinition(propagationBehavior).apply {
        this.isolationLevel = isolationLevel
        if (readOnly) this.isReadOnly = true
        if (timeout != null) this.timeout = timeout
    }
    val trxOp = TransactionalOperator.create(this, trxDef)
    trxOp.executeAndAwait {
        block(it)
    }
}
