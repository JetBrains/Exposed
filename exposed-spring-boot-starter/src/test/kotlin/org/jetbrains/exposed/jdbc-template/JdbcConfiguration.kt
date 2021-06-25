package org.jetbrains.exposed.`jdbc-template`

import org.jetbrains.exposed.spring.SpringTransactionManager
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate

@Configuration
open class JdbcConfiguration {
    @Bean
    @Qualifier("operations1")
    open fun operations1(transactionManager: SpringTransactionManager): TransactionOperations {
        return TransactionTemplate(transactionManager)
    }

    @Bean
    @Qualifier("operations2")
    open fun operations2() = TransactionOperations.withoutTransaction()
}
