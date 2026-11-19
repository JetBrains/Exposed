package org.jetbrains.exposed.v1.spring.data.autoconfigure

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.ExposedRepositoriesConfig
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryConfigExtension
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Import
import javax.sql.DataSource

/**
 * Auto-configuration for Exposed Spring Data repositories.
 *
 * Activates repository scanning in the auto-configuration packages of the application (by default, the package of the
 * class annotated with `@SpringBootApplication` and its sub-packages), the same way `@EnableExposedRepositories`
 * would. The configuration backs off as soon as the application declares `@EnableExposedRepositories` itself.
 *
 * Repository scanning can be disabled with the property `spring.data.exposed.repositories.enabled=false`, and the
 * bootstrap mode can be configured with `spring.data.exposed.repositories.bootstrap-mode`.
 *
 * Repositories use the [org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager] registered by
 * [ExposedAutoConfiguration] for their transactions.
 */
@AutoConfiguration(after = [ExposedAutoConfiguration::class])
@ConditionalOnBean(DataSource::class)
@ConditionalOnClass(ExposedCrudRepository::class)
@ConditionalOnMissingBean(
    type = [EXPOSED_REPOSITORY_FACTORY_BEAN_CLASS_NAME],
    value = [ExposedRepositoryConfigExtension::class]
)
@ConditionalOnBooleanProperty(name = ["spring.data.exposed.repositories.enabled"], matchIfMissing = true)
@Import(ExposedRepositoriesRegistrar::class)
open class ExposedRepositoriesAutoConfiguration

private const val EXPOSED_REPOSITORY_FACTORY_BEAN_CLASS_NAME =
    "org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactoryBean"

/**
 * Placeholder configuration whose [ExposedRepositoriesConfig] annotation provides the default attributes
 * used by the auto-configured repository scanning.
 */
@OptIn(InternalApi::class)
@ExposedRepositoriesConfig(transactionManagerRef = SPRING_TRANSACTION_MANAGER_BEAN_NAME)
internal class EnableExposedRepositoriesConfiguration

/** Name of the transaction manager bean registered by [ExposedAutoConfiguration.springTransactionManager]. */
internal const val SPRING_TRANSACTION_MANAGER_BEAN_NAME = "springTransactionManager"
