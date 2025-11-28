package org.jetbrains.exposed.v1.spring.boot.autoconfigure

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.spring.boot.DatabaseInitializer
import org.jetbrains.exposed.v1.spring.transaction.ExposedSpringTransactionAttributeSource
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

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
@AutoConfiguration(after = [DataSourceAutoConfiguration::class])
@EnableTransactionManagement
open class ExposedAutoConfiguration(private val applicationContext: ApplicationContext) {

    @Value($$"${spring.exposed.excluded-packages:}#{T(java.util.Collections).emptyList()}")
    private lateinit var excludedPackages: List<String>

    @Value($$"${spring.exposed.show-sql:false}")
    private var showSql: Boolean = false

    /**
     * Returns a [SpringTransactionManager] instance using the specified [datasource] and [databaseConfig].
     *
     * To enable logging of all transaction queries by the SpringTransactionManager instance, set the property
     * `spring.exposed.show-sql` to `true` in the application.properties file.
     */
    @Bean
    open fun springTransactionManager(datasource: DataSource, databaseConfig: DatabaseConfig): SpringTransactionManager {
        return SpringTransactionManager(datasource, databaseConfig, showSql)
    }

    /**
     * Database config with default values
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseConfig::class)
    open fun databaseConfig(): DatabaseConfig {
        return DatabaseConfig {}
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
    open fun databaseInitializer() = DatabaseInitializer(applicationContext, excludedPackages)

    /**
     * Returns an [ExposedSpringTransactionAttributeSource] instance.
     *
     * To enable rollback when ExposedSQLException is Thrown
     *
     * '@Primary' annotation is used to avoid conflict with default TransactionAttributeSource bean
     * than enable when use '@EnableTransactionManagement'
     */
    @Bean
    @Primary
    open fun exposedSpringTransactionAttributeSource(): ExposedSpringTransactionAttributeSource {
        return ExposedSpringTransactionAttributeSource()
    }
}
