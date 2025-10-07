package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.TransactionManagementConfigurer
import org.springframework.transaction.support.TransactionTemplate

@Configuration
@EnableTransactionManagement
/*(mode = AdviceMode.ASPECTJ, proxyTargetClass = true)*/
open class TestConfig : TransactionManagementConfigurer {

    @Bean
    open fun ds(): EmbeddedDatabase = EmbeddedDatabaseBuilder().setName(
        "embeddedTest"
    ).setType(EmbeddedDatabaseType.H2).build()

    @Bean
    override fun annotationDrivenTransactionManager(): PlatformTransactionManager = SpringTransactionManager(ds(), DatabaseConfig { useNestedTransactions = true })

    @Bean
    open fun service(): Service = Service()

    @Bean
    open fun mixedTransactionService(): MixedTransactionService = MixedTransactionService()
}

/**
 * Copy
 */
@RunWith(SpringJUnit4ClassRunner::class)
@ContextConfiguration(classes = [TestConfig::class])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Suppress("UnnecessaryAbstractClass")
abstract class SpringTransactionTestBase {

    @Autowired
    lateinit var ctx: ApplicationContext

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager
}

fun PlatformTransactionManager.execute(
    propagationBehavior: Int = TransactionDefinition.PROPAGATION_REQUIRED,
    isolationLevel: Int = TransactionDefinition.ISOLATION_DEFAULT,
    readOnly: Boolean = false,
    timeout: Int? = null,
    block: (TransactionStatus) -> Unit
) {
    if (this !is SpringTransactionManager) error("Wrong txManager instance: ${this.javaClass.name}")
    val tt = TransactionTemplate(this)
    tt.propagationBehavior = propagationBehavior
    tt.isolationLevel = isolationLevel
    if (readOnly) tt.isReadOnly = true
    if (timeout != null) tt.timeout = timeout
    tt.executeWithoutResult {
        block(it)
    }
}
