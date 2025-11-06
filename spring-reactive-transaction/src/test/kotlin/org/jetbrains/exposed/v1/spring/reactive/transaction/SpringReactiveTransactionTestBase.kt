package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
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
    open fun cxFactory(): ConnectionFactory = ConnectionFactories.get(TestDB.H2_V2.connection.invoke())

    @Bean
    override fun annotationDrivenTransactionManager(): ReactiveTransactionManager = SpringReactiveTransactionManager(
        cxFactory(),
        R2dbcDatabaseConfig { useNestedTransactions = true }
    )
}

@RunWith(SpringJUnit4ClassRunner::class)
@ContextConfiguration(classes = [TestConfig::class])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Suppress("UnnecessaryAbstractClass")
abstract class SpringReactiveTransactionTestBase {

    @Autowired
    lateinit var ctx: ApplicationContext

    @Autowired
    lateinit var transactionManager: ReactiveTransactionManager
}

suspend fun ReactiveTransactionManager.execute(
    propagationBehavior: Int = TransactionDefinition.PROPAGATION_REQUIRED,
    isolationLevel: Int = TransactionDefinition.ISOLATION_DEFAULT,
    readOnly: Boolean = false,
    timeout: Int? = null,
    block: suspend (ReactiveTransaction) -> Unit
) {
    if (this !is SpringReactiveTransactionManager) error("Wrong txManager instance: ${this.javaClass.name}")
    val td = DefaultTransactionDefinition(propagationBehavior).apply {
        this.isolationLevel = isolationLevel
        if (readOnly) this.isReadOnly = true
        if (timeout != null) this.timeout = timeout
    }
    val to = TransactionalOperator.create(this, td)
    to.executeAndAwait {
        block(it)
    }
}
