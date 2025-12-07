@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`database-client`

import org.jetbrains.exposed.v1.spring.reactive.transaction.SpringReactiveTransactionManager
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.support.DefaultTransactionDefinition

@Configuration
open class R2dbcConfiguration {
    @Bean
    @Qualifier("operator1")
    open fun operator1(transactionManager: SpringReactiveTransactionManager): TransactionalOperator {
        return TransactionalOperator.create(transactionManager)
    }

    @Bean
    @Qualifier("operator2")
    open fun operator2(transactionManager: SpringReactiveTransactionManager): TransactionalOperator {
        // mostly equivalent to JDBC TransactionOperations.withoutTransaction()
        val noTransactionDefinition = DefaultTransactionDefinition().apply {
            // no support for current transaction or transaction synchronization
            this.propagationBehavior = TransactionDefinition.PROPAGATION_NEVER
        }
        return TransactionalOperator.create(transactionManager, noTransactionDefinition)
    }
}
