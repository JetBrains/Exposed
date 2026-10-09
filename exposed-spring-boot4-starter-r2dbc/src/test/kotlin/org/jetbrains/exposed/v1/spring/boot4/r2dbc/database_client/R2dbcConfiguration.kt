@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`database-client`

import org.jetbrains.exposed.v1.spring7.reactive.transaction.SpringReactiveTransactionManager
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
        // this operator must run without a transaction to match the JDBC variant, which the propagation level ensures
        val trxDef = DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NOT_SUPPORTED)
        return TransactionalOperator.create(transactionManager, trxDef)
    }
}
