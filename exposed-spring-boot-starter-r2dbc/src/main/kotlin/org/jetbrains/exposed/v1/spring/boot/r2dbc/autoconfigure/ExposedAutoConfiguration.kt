package org.jetbrains.exposed.v1.spring.boot.r2dbc.autoconfigure

import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.spring.boot.r2dbc.DatabaseInitializer
import org.jetbrains.exposed.v1.spring.reactive.transaction.ExposedSpringReactiveTransactionAttributeSource
import org.jetbrains.exposed.v1.spring.reactive.transaction.SpringReactiveTransactionManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.EnableTransactionManagement

/**
 * Main configuration class for Exposed that can be automatically applied by Spring Boot.
 *
 * This should be applied on a Spring configuration class using:
 * `@ImportAutoConfiguration(ExposedAutoConfiguration::class)`
 *
 * **Note** As part of the configuration, `@EnableTransactionManagement` is added without setting any attributes.
 * This means that all attributes have their default values, including `mode = AdviceMode.PROXY` and
 * `proxyTargetClass = false`. If the type of proxy mechanism is unexpected, the attributes can be set to the
 * required values in a separate `@EnableTransactionManagement` on the main configuration class or in a configuration
 * file using `spring.aop.proxy-target-class`.
 *
 * @property applicationContext The Spring ApplicationContext container responsible for managing beans.
 */
@AutoConfiguration(after = [R2dbcAutoConfiguration::class])
@EnableTransactionManagement
open class ExposedAutoConfiguration(private val applicationContext: ApplicationContext) {

    @Value($$"${spring.exposed.excluded-packages:}#{T(java.util.Collections).emptyList()}")
    private lateinit var excludedPackages: List<String>

    @Value($$"${spring.exposed.show-sql:false}")
    private var showSql: Boolean = false

    /**
     * Returns a [SpringReactiveTransactionManager] instance using the specified [connectionFactory] and [databaseConfig].
     *
     * To enable logging of all transaction queries by the SpringTransactionManager instance, set the property
     * `spring.exposed.show-sql` to `true` in the application.properties file.
     *
     * [databaseConfig] sets the configuration that defines custom properties to be used with connections. An attempt will
     * be made to resolve an appropriate [DatabaseDialect] from the provided [connectionFactory] metadata. To avoid any
     * unexpected exceptions, it is strongly recommended to provide a configuration that specifies `R2dbcDatabaseConfig.explicitDialect`.
     *
     * @throws IllegalStateException If an [R2dbcDatabaseConfig] is detected with a null `explicitDialect` property and
     * a dialect cannot be resolved from the [connectionFactory] metadata.
     */
    @Bean
    open fun springReactiveTransactionManager(
        connectionFactory: ConnectionFactory,
        databaseConfig: R2dbcDatabaseConfig.Builder
    ): SpringReactiveTransactionManager {
        if (databaseConfig.explicitDialect == null) {
            databaseConfig.explicitDialect = resolveDatabaseDialect(connectionFactory.metadata.name)
        }
        return SpringReactiveTransactionManager(connectionFactory, databaseConfig, showSql)
    }

    /**
     * Database configuration with default values
     */
    @Bean
    @ConditionalOnMissingBean(R2dbcDatabaseConfig.Builder::class)
    open fun databaseConfig(): R2dbcDatabaseConfig.Builder {
        return R2dbcDatabaseConfig.Builder()
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
    open fun databaseInitializer(): DatabaseInitializer = DatabaseInitializer(applicationContext, excludedPackages)

    /**
     * Returns an [ExposedSpringReactiveTransactionAttributeSource] instance.
     *
     * To enable rollback when `ExposedR2dbcException` is thrown.
     *
     * '@Primary' annotation is used to avoid conflict with default TransactionAttributeSource bean
     * then enable when using '@EnableTransactionManagement'.
     */
    @Bean
    @Primary
    open fun exposedSpringReactiveTransactionAttributeSource(): ExposedSpringReactiveTransactionAttributeSource {
        return ExposedSpringReactiveTransactionAttributeSource()
    }

    private fun resolveDatabaseDialect(factoryName: String): DatabaseDialect {
        return when {
            factoryName.startsWith("MySQL", ignoreCase = true) -> MysqlDialect()
            factoryName.startsWith("MariaDB", ignoreCase = true) -> MariaDBDialect()
            factoryName.startsWith("H2", ignoreCase = true) -> H2Dialect()
            factoryName.startsWith("PostgreSQL", ignoreCase = true) -> PostgreSQLDialect()
            factoryName.startsWith("Oracle Database", ignoreCase = true) -> OracleDialect()
            factoryName.startsWith("Microsoft SQL Server", ignoreCase = true) -> SQLServerDialect()
            else -> error(
                "Cannot resolve DatabaseDialect for the provided connection factory product: $factoryName; " +
                    "Manually specify the mapping dialect via the property R2dbcDatabaseConfig.explicitDialect."
            )
        }
    }
}
