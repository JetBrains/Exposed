package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.reactor.mono
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.transactions.currentTransactionOrNull
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.asContext
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.springframework.r2dbc.UncategorizedR2dbcException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.reactive.AbstractReactiveTransactionManager
import org.springframework.transaction.reactive.GenericReactiveTransaction
import org.springframework.transaction.reactive.TransactionSynchronizationManager
import reactor.core.publisher.Mono
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Transaction Manager implementation that builds on top of Spring's standard reactive transaction workflow.
 *
 * @param connectionFactory The [ConnectionFactory] entry point for an R2DBC driver when getting a connection.
 * @param databaseConfig The configuration that defines custom properties to be used with connections.
 * At minimum, a configuration must be provided that specifies `DatabaseConfig.explicitDialect`.
 * @property showSql Whether transaction queries should be logged. Defaults to `false`.
 */
class SpringReactiveTransactionManager(
    connectionFactory: ConnectionFactory,
    databaseConfig: R2dbcDatabaseConfig.Builder,
    private val showSql: Boolean = false,
) : AbstractReactiveTransactionManager() {

    private val database: R2dbcDatabase = R2dbcDatabase.connect(
        connectionFactory = connectionFactory,
        databaseConfig = databaseConfig
    )

    override fun doGetTransaction(
        synchronizationManager: TransactionSynchronizationManager
    ): Any {
        return ExposedTransactionObject(database = database)
    }

    override fun doSuspend(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any
    ): Mono<in Any> {
        return Mono.defer {
            val trxObject = transaction as ExposedTransactionObject

            val holder = SuspendedObject(
                transaction = trxObject.getCurrentTransaction() ?: error("No transaction to suspend"),
            ).apply {
                @OptIn(InternalApi::class)
                ThreadLocalTransactionsStack.popTransaction()

                try {
                    synchronizationManager.unbindResource(database)
                } catch (e: IllegalStateException) {
                    println(e.message)
                    // do nothing
                }
            }

            Mono.just(holder)
        }
    }

    override fun doResume(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any?,
        suspendedResources: Any
    ): Mono<Void?> {
        return Mono.defer {
            val suspendedObject = suspendedResources as SuspendedObject

            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.pushTransaction(suspendedObject.transaction)

            try {
                @OptIn(InternalApi::class)
                synchronizationManager.bindResource(database, suspendedObject.transaction.asContext())
            } catch (_: IllegalStateException) {
                // do nothing
            }

            Mono.empty()
        }
    }

    override fun isExistingTransaction(transaction: Any): Boolean {
        val trxObject = transaction as ExposedTransactionObject

        return trxObject.getCurrentTransaction() != null
    }

    override fun doBegin(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any,
        definition: TransactionDefinition
    ): Mono<Void?> {
        return Mono.defer {
            val trxObject = transaction as ExposedTransactionObject

            @OptIn(InternalApi::class)
            val currentTransaction = currentTransactionOrNull() as R2dbcTransaction?
            val outerTransactionToUse = if (currentTransaction?.db == database) {
                currentTransaction
            } else {
                null
            }

            val newTransaction = trxObject.database.transactionManager.newTransaction(
                isolation = definition.isolationLevel.resolveIsolationLevel(),
                readOnly = definition.isReadOnly,
                outerTransaction = outerTransactionToUse
            ).apply {
                if (definition.timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                    queryTimeout = definition.timeout
                }

                if (showSql) {
                    addLogger(StdOutSqlLogger)
                }
            }

            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.pushTransaction(newTransaction)

            trxObject.isNewConnection = outerTransactionToUse == null
            // TODO - Improve conditional check. Adding it makes some PROPAGATED_NESTED tests fail while others pass
//            if (trxObject.isNewConnection) {
//                try {
//                    @OptIn(InternalApi::class)
//                    synchronizationManager.bindResource(database, newTransaction.asContext())
//                } catch (_: IllegalStateException) {
//                    // do nothing
//                }
//            }
            try {
                @OptIn(InternalApi::class)
                synchronizationManager.bindResource(database, newTransaction.asContext())
            } catch (_: IllegalStateException) {
                // do nothing
            }

            Mono.just(newTransaction)
        }.then()
    }

    override fun doCommit(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void?> {
        return mono(synchronizationManager.getStoredContext()) {
            val trxObject = status.transaction as ExposedTransactionObject

            trxObject.commit()

            null
        }
    }

    override fun doRollback(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void?> {
        return mono(synchronizationManager.getStoredContext()) {
            val trxObject = status.transaction as ExposedTransactionObject

            trxObject.rollback()

            null
        }
    }

    override fun doCleanupAfterCompletion(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any
    ): Mono<Void?> {
        return Mono.defer {
            val trxObject = transaction as ExposedTransactionObject

            trxObject.cleanUpTransactionIfIsPossible {
                closeStatements(it)

                @OptIn(InternalApi::class)
                ThreadLocalTransactionsStack.popTransaction()

                val contextToUse = synchronizationManager.getStoredContext()
                // TODO - Improve conditional check. Removing it makes some PROPAGATED_NESTED tests fail while others pass
                if (trxObject.isNewConnection) {
                    try {
                        synchronizationManager.unbindResource(database)
                    } catch (_: IllegalStateException) {
                        // do nothing
                    }
                }

                mono(contextToUse) {
                    it.close()

                    null
                }
            }
        }
    }

    private fun TransactionSynchronizationManager.getStoredContext(): CoroutineContext {
        return this.getResource(database) as? CoroutineContext ?: EmptyCoroutineContext
    }

    private fun closeStatements(transaction: R2dbcTransaction) {
        val currentStatement = transaction.currentStatement
        @Suppress("TooGenericExceptionCaught")
        try {
            currentStatement?.let {
                // No Statement.close() in R2DBC
                transaction.currentStatement = null
            }
            transaction.closeExecutedStatements()
        } catch (error: Exception) {
            exposedLogger.warn("Statements close failed", error)
        }
    }

    override fun doSetRollbackOnly(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void?> {
        val trxObject = status.transaction as ExposedTransactionObject

        return trxObject.setRollbackOnly()
    }

    private data class SuspendedObject(
        val transaction: R2dbcTransaction
    )

    private data class ExposedTransactionObject(
        val database: R2dbcDatabase
    ) {
        private var isRollback: Boolean = false

        var isNewConnection: Boolean = false

        fun cleanUpTransactionIfIsPossible(block: (transaction: R2dbcTransaction) -> Mono<Void?>): Mono<Void?> {
            val currentTransaction = getCurrentTransaction()
            return currentTransaction?.let { block(it) } ?: Mono.empty()
        }

        @Suppress("TooGenericExceptionCaught")
        suspend fun commit() {
            try {
                getCurrentTransaction()?.commit()
            } catch (error: R2dbcException) {
                throw UncategorizedR2dbcException(error.message.orEmpty(), null, error)
            } catch (error: Exception) {
                throw TransactionSystemException(error.message.orEmpty(), error)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        suspend fun rollback() {
            try {
                getCurrentTransaction()?.rollback()
            } catch (error: R2dbcException) {
                throw UncategorizedR2dbcException(error.message.orEmpty(), null, error)
            } catch (error: Exception) {
                throw TransactionSystemException(error.message.orEmpty(), error)
            }
        }

        @OptIn(InternalApi::class)
        fun getCurrentTransaction(): R2dbcTransaction? {
            return ThreadLocalTransactionsStack.getTransactionOrNull(database) as R2dbcTransaction?
        }

        fun setRollbackOnly(): Mono<Void?> {
            return Mono.defer {
                isRollback = true

                Mono.empty()
            }
        }
    }
}

internal fun Int.resolveIsolationLevel(): IsolationLevel? = when (this) {
    TransactionDefinition.ISOLATION_READ_UNCOMMITTED -> IsolationLevel.READ_UNCOMMITTED
    TransactionDefinition.ISOLATION_READ_COMMITTED -> IsolationLevel.READ_COMMITTED
    TransactionDefinition.ISOLATION_REPEATABLE_READ -> IsolationLevel.REPEATABLE_READ
    TransactionDefinition.ISOLATION_SERIALIZABLE -> IsolationLevel.SERIALIZABLE
    TransactionDefinition.ISOLATION_DEFAULT -> null
    else -> error("Unsupported Int as IsolationLevel: $this")
}
