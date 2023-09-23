package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.DatabaseConfig
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
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.TransactionManagementConfigurer

@Configuration
@EnableTransactionManagement
/*(mode = AdviceMode.ASPECTJ, proxyTargetClass = true)*/
open class TestConfig : TransactionManagementConfigurer {

    @Bean
    open fun ds(): EmbeddedDatabase = EmbeddedDatabaseBuilder().setName(
        "embeddedTest"
    ).setType(EmbeddedDatabaseType.H2).build()

    @Bean
    override fun annotationDrivenTransactionManager(): PlatformTransactionManager = SpringTransactionManager(
        ds(),
        DatabaseConfig { useNestedTransactions = true }
    )

    @Bean
    open fun service(): Service = Service()
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
