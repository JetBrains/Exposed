package org.jetbrains.exposed.v1.spring7.reactive.transaction

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SpringTransactionManagerDestroyTest {
    @Test
    suspend fun `closing the context unregisters the database`() {
        val db = newContext().use { it.inTransaction { this.db } }

        assertFailsWith<IllegalStateException> { TransactionManager.managerFor(db) }
        assertNotEquals(db, TransactionManager.primaryDatabase)
    }

    @Test
    suspend fun `closing one context keeps the database of another context registered`() {
        newContext().use { other ->
            val closedDb = newContext().use { it.inTransaction { db } }
            val otherDb = other.inTransaction { db }

            assertFailsWith<IllegalStateException> { TransactionManager.managerFor(closedDb) }
            assertEquals(otherDb, TransactionManager.managerFor(otherDb).db)
            assertEquals(true, other.inTransaction { exec("SELECT 1") { true }?.singleOrNull() })
        }
    }

    private fun newContext() = AnnotationConfigApplicationContext().apply {
        registerBean(ConnectionFactory::class.java, Supplier<ConnectionFactory> { newCxFactory() })
        registerBean(
            SpringReactiveTransactionManager::class.java,
            Supplier {
                SpringReactiveTransactionManager(
                    getBean(ConnectionFactory::class.java),
                    R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
                )
            }
        )
        refresh()
    }

    private fun newCxFactory() = ConnectionFactories.get("r2dbc:h2:mem:///destroyTest;DB_CLOSE_DELAY=-1;")

    private suspend fun <T> AnnotationConfigApplicationContext.inTransaction(
        block: suspend R2dbcTransaction.() -> T
    ): T {
        val trxOp = TransactionalOperator.create(getBean(SpringReactiveTransactionManager::class.java))
        return withExposedReactiveContext {
            trxOp.executeAndAwait {
                TransactionManager.current().block()
            }
        }
    }
}
