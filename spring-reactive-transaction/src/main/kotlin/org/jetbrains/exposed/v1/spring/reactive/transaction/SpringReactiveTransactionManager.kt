package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.reactor.mono
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.transactions.currentTransactionOrNull
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.jetbrains.exposed.v1.r2dbc.withTransactionContext
import org.springframework.r2dbc.UncategorizedR2dbcException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.reactive.AbstractReactiveTransactionManager
import org.springframework.transaction.reactive.GenericReactiveTransaction
import org.springframework.transaction.reactive.TransactionSynchronizationManager
import reactor.core.publisher.Mono

/**
 * Transaction Manager implementation that builds on top of Spring's standard reactive transaction workflow.
 *
 * @param connectionFactory The [ConnectionFactory] entry point for an R2DBC driver when getting a connection.
 * @param databaseConfig The configuration that defines custom properties to be used with connections.
 * At minimum, a configuration must be provided that specifies `R2dbcDatabaseConfig.explicitDialect`.
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
        val holder = ExposedTransactionObject(database = database)
        val outer = holder.getCurrentTransaction()

        // Only clears up leftovers between transactions, to prevent invalid re-use;
        // Will not be able to clean the final active transaction
        if (outer != null && synchronizationManager.getResource(database) == null) {
            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.popTransaction()
        }

        return holder
    }

    override fun doSuspend(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any
    ): Mono<in Any> {
        return Mono.defer {
            val trxObject = transaction as ExposedTransactionObject

            val currentTransaction = trxObject.getCurrentTransaction()

            val holder = SuspendedObject(
                transaction = currentTransaction ?: error("No transaction to suspend"),
            )
            synchronizationManager.unbindResource(database)

            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.popTransaction()

            Mono.just(holder)
        }
    }

    override fun doResume(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any?,
        suspendedResources: Any
    ): Mono<Void> {
        return Mono.defer {
            val suspendedObject = suspendedResources as SuspendedObject

            val suspendedTransaction = suspendedObject.transaction

            synchronizationManager.bindResource(database, suspendedTransaction)

            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.pushTransaction(suspendedTransaction)

            Mono.empty()
        }
    }

    override fun isExistingTransaction(transaction: Any): Boolean {
        val trxObject = transaction as ExposedTransactionObject

        val currentTransaction = trxObject.getCurrentTransaction()

        return currentTransaction != null
    }

    override fun doBegin(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any,
        definition: TransactionDefinition
    ): Mono<Void> {
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

            trxObject.isNewConnection = newTransaction.outerTransaction == null || trxObject.isNestedTransactionAllowed
            if (trxObject.isNewConnection) {
                // otherwise a PROPAGATION_NESTED transaction would incorrectly have the context of its outer
                // transaction used when doCommit() or doRollback() is invoked
                synchronizationManager.unbindResourceIfPossible(database)

                synchronizationManager.bindResource(database, newTransaction)
            }

            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.pushTransaction(newTransaction)

            Mono.just(newTransaction)
        }.then()
    }

    override fun doCommit(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void> {
        return Mono.defer {
            val trxObject = status.transaction as ExposedTransactionObject

            mono {
                @OptIn(InternalApi::class)
                withTransactionContext(synchronizationManager.getResourceOrThrow()) {
                    trxObject.commit()
                }

                null
            }
        }
    }

    override fun doRollback(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void> {
        return Mono.defer {
            val trxObject = status.transaction as ExposedTransactionObject

            mono {
                @OptIn(InternalApi::class)
                withTransactionContext(synchronizationManager.getResourceOrThrow()) {
                    trxObject.rollback()
                }

                null
            }
        }
    }

    override fun doCleanupAfterCompletion(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any
    ): Mono<Void> {
        return Mono.defer {
            val trxObject = transaction as ExposedTransactionObject

            mono {
                @OptIn(InternalApi::class)
                withTransactionContext(synchronizationManager.getResourceOrThrow()) {
                    val completedTransaction = trxObject.getCurrentTransaction()

                    completedTransaction
                        ?.let {
                            clearStatements(it)

                            if (trxObject.isNewConnection) {
                                synchronizationManager.unbindResource(database)

                                // otherwise a PROPAGATION_NESTED transaction would incorrectly have the context of its
                                // now closed inner transaction used when doCommit() or doRollback() is later invoked
                                it.outerTransaction?.let { outer ->
                                    synchronizationManager.bindResource(database, outer)
                                }
                            }

                            it.close()
                        }
                }

                null
            }
        }
    }

    private fun clearStatements(transaction: R2dbcTransaction) {
        val currentStatement = transaction.currentStatement
        currentStatement?.let {
            // No Statement.close() in R2DBC
            transaction.currentStatement = null
        }
        transaction.clearExecutedStatements()
    }

    override fun doSetRollbackOnly(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void> {
        return Mono.fromRunnable {
            val trxObject = status.transaction as ExposedTransactionObject

            trxObject.setRollbackOnly()
        }
    }

    private fun TransactionSynchronizationManager.getResourceOrThrow(): R2dbcTransaction {
        return this.getResource(database) as? R2dbcTransaction ?: error("No transaction value bound to the current context")
    }

    private data class SuspendedObject(
        val transaction: R2dbcTransaction
    )

    private data class ExposedTransactionObject(
        val database: R2dbcDatabase
    ) {
        private var isRollback: Boolean = false

        val isNestedTransactionAllowed = database.config.useNestedTransactions
        var isNewConnection: Boolean = false

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

        fun setRollbackOnly() {
            isRollback = true
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
