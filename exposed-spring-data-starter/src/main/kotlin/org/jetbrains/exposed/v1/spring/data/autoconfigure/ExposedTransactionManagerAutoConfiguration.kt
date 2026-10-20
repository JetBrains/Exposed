package org.jetbrains.exposed.v1.spring.data.autoconfigure

import org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager

@AutoConfiguration(after = [ExposedAutoConfiguration::class])
@ConditionalOnBean(SpringTransactionManager::class)
internal open class ExposedTransactionManagerAutoConfiguration {
    @Bean(name = [TRANSACTION_MANAGER_BEAN_NAME])
    @Primary
    @ConditionalOnMissingBean(name = [TRANSACTION_MANAGER_BEAN_NAME])
    open fun transactionManagerAlias(springTransactionManager: SpringTransactionManager): PlatformTransactionManager =
        springTransactionManager
}

private const val TRANSACTION_MANAGER_BEAN_NAME = "transactionManager"
