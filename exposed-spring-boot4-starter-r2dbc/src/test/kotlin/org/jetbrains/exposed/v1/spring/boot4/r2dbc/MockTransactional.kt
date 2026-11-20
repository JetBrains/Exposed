package org.jetbrains.exposed.v1.spring.boot4.r2dbc

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.spring7.reactive.transaction.SpringReactiveTransactionManager
import org.jetbrains.exposed.v1.spring7.reactive.transaction.withExposedReactiveContext
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import org.springframework.transaction.support.DefaultTransactionDefinition

/**
 * Invokes [runTest] with the [testBody] executed by a [TransactionalOperator] that is set up to
 * follow the same rollback rules as `@Transactional`.
 *
 * Currently, `@Transactional` in Spring's `TestContext` is only configured to find a `PlatformTransactionManager`,
 * so it is completely unusable for Spring-R2dbc unit tests.
 *
 * [Open Issue](https://github.com/spring-projects/spring-framework/issues/24226)
 *
 * This same approach is applied throughout `spring7-reactive-transaction` module's tests.
 */
internal fun runTestWithMockTransactional(
    transactionManager: SpringReactiveTransactionManager?,
    testBody: suspend TestScope.(ReactiveTransaction) -> Unit
) {
    if (transactionManager == null) error("Failed to load transaction manager at start-up")

    val trxDef = DefaultTransactionDefinition()

    runTest {
        val trxOp = TransactionalOperator.create(transactionManager, trxDef)
        withExposedReactiveContext {
            trxOp.executeAndAwait {
                testBody(it)
                it.setRollbackOnly()
            }
        }
    }
}
