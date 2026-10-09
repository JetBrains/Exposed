package org.jetbrains.exposed.v1.spring.boot4.r2dbc.autoconfigure

import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.spring.boot4.r2dbc.DatabaseInitializer
import org.jetbrains.exposed.v1.spring7.reactive.transaction.EnableExposedReactiveTransactionManagement
import org.jetbrains.exposed.v1.spring7.reactive.transaction.ExposedSpringTransactionAttributeSource
import org.jetbrains.exposed.v1.spring7.reactive.transaction.SpringReactiveTransactionManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Role
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * Main configuration class for Exposed that can be automatically applied by Spring Boot with R2DBC.
 *
 * This should be applied on a Spring configuration class using:
 * `@ImportAutoConfiguration(ExposedReactiveAutoConfiguration::class)`
 *
 * **Note** As part of the configuration, `@EnableTransactionManagement` is added without setting any attributes.
 * This means that all attributes have their default values, including `mode = AdviceMode.PROXY` and
 * `proxyTargetClass = false`. If the type of proxy mechanism is unexpected, the attributes can be set to the
 * required values in a separate `@EnableTransactionManagement` on the main configuration class or in a configuration
 * file using `spring.aop.proxy-target-class`.
 *
 * **Note** As part of the configuration, [EnableExposedReactiveTransactionManagement] is also added to properly set up
 * the built-in [SpringReactiveTransactionManager] for working alongside suspend `@Transactional` methods.
 *
 * @property applicationContext The Spring ApplicationContext container responsible for managing beans.
 */
@AutoConfiguration(after = [R2dbcAutoConfiguration::class])
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@EnableTransactionManagement
@EnableExposedReactiveTransactionManagement
open class ExposedReactiveAutoConfiguration(private val applicationContext: ApplicationContext) {

    @Value($$"${spring.exposed.excluded-packages:}#{T(java.util.Collections).emptyList()}")
    private lateinit var excludedPackages: List<String>

    @Value($$"${spring.exposed.database-driver:}")
    private lateinit var databaseDriver: String

    @Value($$"${spring.exposed.show-sql:false}")
    private var showSql: Boolean = false

    /**
     * Returns a [SpringReactiveTransactionManager] instance using the specified [connectionFactory] and [databaseConfig].
     *
     * To enable logging of all transaction queries by the SpringReactiveTransactionManager instance, set the property
     * `spring.exposed.show-sql` to `true` in the application.properties file.
     */
    @Bean
    open fun springTransactionManager(
        connectionFactory: ConnectionFactory,
        databaseConfig: R2dbcDatabaseConfig.Builder,
    ): SpringReactiveTransactionManager {
        return SpringReactiveTransactionManager(connectionFactory, databaseConfig, showSql)
    }

    /**
     * Database config with default values.
     *
     * At minimum, the property `spring.exposed.database-driver` must be set to avoid incompatibility issues
     * when a future `R2dbcDatabase` attempts to get a connection from Spring's created `ConnectionFactory`.
     */
    @Bean
    @ConditionalOnMissingBean(R2dbcDatabaseConfig.Builder::class)
    @ConditionalOnProperty("spring.exposed.database-driver", havingValue = "", matchIfMissing = false)
    open fun databaseConfig(): R2dbcDatabaseConfig.Builder {
        return R2dbcDatabaseConfig {
            setExplicitDialect(this@ExposedReactiveAutoConfiguration.databaseDriver)
        }
    }

    /**
     * Returns a [DatabaseInitializer] that auto-creates the database schema, if enabled by the property
     * `spring.exposed.generate-ddl` in the application.properties file.
     *
     * The property `spring.exposed.excluded-packages` can be used to ensure that tables in specified packages are
     * not auto-created.
     */
    @Bean
    @ConditionalOnProperty("spring.exposed.generate-ddl", havingValue = "true", matchIfMissing = false)
    open fun databaseInitializer(
        springTransactionManager: SpringReactiveTransactionManager,
    ): DatabaseInitializer {
        return DatabaseInitializer(applicationContext, excludedPackages, TransactionalOperator.create(springTransactionManager))
    }

    /**
     * Returns an [ExposedSpringTransactionAttributeSource] instance.
     *
     * To enable rollback when ExposedSQLException is Thrown
     *
     * '@Primary' annotation is used to avoid conflict with default TransactionAttributeSource bean
     * than enable when use '@EnableTransactionManagement'
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Primary
    open fun exposedSpringTransactionAttributeSource(): ExposedSpringTransactionAttributeSource {
        return ExposedSpringTransactionAttributeSource()
    }

    private fun R2dbcDatabaseConfig.Builder.setExplicitDialect(input: String) {
        explicitDialect = when (input) {
            "h2" -> H2Dialect()
            "postgresql" -> PostgreSQLDialect()
            "mysql" -> MysqlDialect()
            "mariadb" -> MariaDBDialect()
            "oracle" -> OracleDialect()
            "mssql" -> SQLServerDialect()
            else -> error("Unsupported driver dialect configured: $input")
        }
    }
}
