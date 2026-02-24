package org.jetbrains.exposed.v1.spring7.reactive.transaction

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactor.mono
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.transactions.transactionScope
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.viewThreadStack
import org.reactivestreams.Publisher
import org.springframework.r2dbc.UncategorizedR2dbcException
import org.springframework.r2dbc.connection.ConnectionHolder
import org.springframework.transaction.CannotCreateTransactionException
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
    private val connectionFactory: ConnectionFactory,
    databaseConfig: R2dbcDatabaseConfig.Builder,
    private val showSql: Boolean = false,
) : AbstractReactiveTransactionManager() {

    private val database: R2dbcDatabase = R2dbcDatabase.connect(
        connectionFactory = connectionFactory,
        databaseConfig = databaseConfig,
    )

    override fun doGetTransaction(
        synchronizationManager: TransactionSynchronizationManager
    ): Any {
        synchronizationManager.printEverything(::doGetTransaction.name)

        return ExposedTransactionObject(
            database = database,
        ).apply {
            connectionHolder = synchronizationManager.getResourceHolderOrNull()
        }
    }

    override fun doSuspend(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any
    ): Mono<in Any> {
        return Mono.defer {
            val trxObject = transaction as ExposedTransactionObject

            synchronizationManager.printEverything(::doSuspend.name)

            synchronizationManager.getResourceOrNull() ?: error("No transaction to suspend")

            Mono
                .justOrEmpty(
                    synchronizationManager.unbindResource(connectionFactory) as ExposedHolderObject
                )
                .doOnSuccess {
                    trxObject.connectionHolder = null

                    @OptIn(InternalApi::class)
                    ThreadLocalTransactionsStack.popTransaction()

                    synchronizationManager.printEverything(::doSuspend.name)
                }
        }
    }

    override fun doResume(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any?,
        suspendedResources: Any
    ): Mono<Void> {
        return Mono.defer {
            val suspendedObject = suspendedResources as ExposedHolderObject

            synchronizationManager.bindResource(connectionFactory, suspendedObject)

            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.pushTransaction(suspendedObject.transaction)

            synchronizationManager.printEverything(::doResume.name)

            Mono.empty()
        }
    }

    override fun isExistingTransaction(transaction: Any): Boolean {
        val trxObject = transaction as ExposedTransactionObject

        println("In existingTransaction with ${trxObject.getCurrentTransaction()}")

        return trxObject.getCurrentTransaction() != null
    }

    override fun doBegin(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any,
        definition: TransactionDefinition
    ): Mono<Void> {
        return Mono.defer {
            val trxObject = transaction as ExposedTransactionObject

            val currentTransaction = synchronizationManager.getResourceOrNull()
            val outerTransactionToUse = if (currentTransaction?.db == database) {
                currentTransaction
            } else {
                null
            }
            synchronizationManager.printEverything(::doBegin.name)

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

            // force new coroutine to start in current thread so that potential callbacks can access correct stack
            val newConnectionMono = mono(Dispatchers.Unconfined) {
                if (trxObject.connectionHolder == null || trxObject.isNestedTransactionAllowed) {
                    trxObject.connectionHolder = ExposedHolderObject(newTransaction.awaitConnection(), newTransaction)
                    trxObject.isNewConnectionHolder = true
                }
                trxObject.connectionHolder?.isSynchronizedWithTransaction = true

                true
            }

            Mono
                .just(newTransaction)
                .doOnSuccess {
                    @OptIn(InternalApi::class)
                    ThreadLocalTransactionsStack.pushTransaction(newTransaction)
                }
                .then(newConnectionMono)
                .doOnSuccess {
                    if (definition.timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                        trxObject.connectionHolder?.setTimeoutInSeconds(definition.timeout)
                    }

                    if (trxObject.isNewConnectionHolder) {
                        // otherwise a PROPAGATION_NESTED transaction would incorrectly have the context of its outer
                        // transaction used when doCommit() or doRollback() is invoked
                        synchronizationManager.unbindResourceIfPossible(connectionFactory) as? ExposedHolderObject

                        synchronizationManager.bindResource(connectionFactory, trxObject.connectionHolder!!)
                    }
                    synchronizationManager.printEverything(::doBegin.name)
                }
                .doOnError { ex ->
                    trxObject.connectionHolder = null

                    @OptIn(InternalApi::class)
                    ThreadLocalTransactionsStack.popTransaction()

                    throw CannotCreateTransactionException("Could not open R2DBC Connection for transaction", ex)
                }
        }
            .then()
    }

    override fun doCommit(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void> {
        val trxObject = status.transaction as ExposedTransactionObject

        synchronizationManager.getResourceOrNull() ?: error("No synchronized transaction to commit")
        synchronizationManager.printEverything(::doCommit.name)

        // force new coroutine to start in current thread so that doCleanupOnCompletion accesses correct stack
        return mono(Dispatchers.Unconfined) {
            trxObject.commit()

            synchronizationManager.printEverything("${::doCommit.name} [inner]")

            null
        }
    }

    override fun doRollback(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void> {
        val trxObject = status.transaction as ExposedTransactionObject

        synchronizationManager.getResourceOrNull() ?: error("No synchronized transaction to rollback")
        synchronizationManager.printEverything(::doRollback.name)

        // force new coroutine to start in current thread so that doCleanupOnCompletion accesses correct stack
        return mono(Dispatchers.Unconfined) {
            trxObject.rollback()

            synchronizationManager.printEverything("${::doRollback.name} [inner]")

            null
        }
    }

    override fun doCleanupAfterCompletion(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any
    ): Mono<Void> {
        return Mono.defer {
            val trxObject = transaction as ExposedTransactionObject

            val completedTransaction = synchronizationManager.getResourceOrNull()?.also {
                clearStatements(it)
            }

            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.popTransaction()

            synchronizationManager.printEverything(::doCleanupAfterCompletion.name)

            if (trxObject.isNewConnectionHolder) {
                val previous = synchronizationManager.unbindResource(connectionFactory) as ExposedHolderObject

                // otherwise a PROPAGATION_NESTED transaction would incorrectly have the context of its
                // now closed inner transaction used when doCommit() or doRollback() is later invoked
                completedTransaction?.outerTransaction?.let { outer ->
                    synchronizationManager.bindResource(connectionFactory, ExposedHolderObject(previous.connection, outer))
                }
            }

            // force new coroutine to start in current thread so that future callbacks can access correct stack
            mono(Dispatchers.Unconfined) {
                completedTransaction?.close()

                synchronizationManager.printEverything(::doCleanupAfterCompletion.name)

                null
            }
                .doOnEach {
                    if (trxObject.isNewConnectionHolder) {
                        trxObject.connectionHolder?.released()
                    }
                    trxObject.connectionHolder?.clear()
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

            synchronizationManager.printEverything(::doSetRollbackOnly.name)

            if (status.isDebug) {
                exposedLogger.debug("Exposed transaction [${status.transactionName}] set rollback-only")
            }

            trxObject.setRollbackOnly()
        }
    }

    private class ExposedHolderObject(
        connection: Connection,
        val transaction: R2dbcTransaction,
    ) : ConnectionHolder(connection)

    @Suppress("UNCHECKED_CAST")
    private suspend fun R2dbcTransaction.awaitConnection(): Connection {
        return (this.connection().connection as Publisher<out Connection>).awaitLast()
    }

    private data class ExposedTransactionObject(
        val database: R2dbcDatabase,
    ) {
        var isNewConnectionHolder: Boolean = false
        var connectionHolder: ExposedHolderObject? = null
        val isNestedTransactionAllowed: Boolean = database.config.useNestedTransactions

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

        fun getCurrentTransaction(): R2dbcTransaction? {
            return connectionHolder?.transaction
        }

        fun setRollbackOnly() {
            getCurrentTransaction()?.isRollback = true
            connectionHolder?.setRollbackOnly()
        }
    }

    private fun TransactionSynchronizationManager.getResourceHolderOrNull(): ExposedHolderObject? {
        return this.getResource(connectionFactory) as? ExposedHolderObject
    }

    private fun TransactionSynchronizationManager.getResourceOrNull(): R2dbcTransaction? {
        return this.getResourceHolderOrNull()?.transaction
    }

    @OptIn(InternalApi::class)
    private fun TransactionSynchronizationManager.printEverything(methodName: String) {
        val resource = this.getResourceOrNull()?.transactionId ?: "NO SPRING TRX"
        println("In $methodName...${viewThreadStack()}\n\tSPRING --> $resource")
    }
}

private var R2dbcTransaction.isRollback: Boolean by transactionScope { false }

/** Returns the rollback status of the current [R2dbcTransaction]. */
internal fun R2dbcTransaction.isMarkedRollback(): Boolean = isRollback

internal fun Int.resolveIsolationLevel(): IsolationLevel? = when (this) {
    TransactionDefinition.ISOLATION_READ_UNCOMMITTED -> IsolationLevel.READ_UNCOMMITTED
    TransactionDefinition.ISOLATION_READ_COMMITTED -> IsolationLevel.READ_COMMITTED
    TransactionDefinition.ISOLATION_REPEATABLE_READ -> IsolationLevel.REPEATABLE_READ
    TransactionDefinition.ISOLATION_SERIALIZABLE -> IsolationLevel.SERIALIZABLE
    TransactionDefinition.ISOLATION_DEFAULT -> null
    else -> error("Unsupported Int as IsolationLevel: $this")
}
