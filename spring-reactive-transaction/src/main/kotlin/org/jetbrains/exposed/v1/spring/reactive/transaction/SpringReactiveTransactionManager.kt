package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactor.mono
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.transactions.currentTransactionOrNull
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.asIsolationLevel
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
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
 * If none is specified, the default configuration values will be used.
 * @property showSql Whether transaction queries should be logged. Defaults to `false`.
 */
class SpringReactiveTransactionManager(
    connectionFactory: ConnectionFactory,
    databaseConfig: R2dbcDatabaseConfig.Builder = R2dbcDatabaseConfig.Builder(),
    private val showSql: Boolean = false,
) : AbstractReactiveTransactionManager() {

    private val database: R2dbcDatabase = R2dbcDatabase.connect(
        connectionFactory = connectionFactory,
        databaseConfig = databaseConfig
    )

    override fun doGetTransaction(
        synchronizationManager: TransactionSynchronizationManager
    ): Any {
        val outer = TransactionManager.currentOrNull()
        require(outer == synchronizationManager.getResource(database)) {
            "Exposed synchronization has deviated from Spring's synchronization manager"
        }

        return ExposedTransactionObject(
            database = database,
            outerTransaction = outer,
        )
    }

    override fun doSuspend(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any
    ): Mono<in Any> {
        return mono {
            val trxObject = transaction as ExposedTransactionObject

            SuspendedObject(
                transaction = trxObject.getCurrentTransaction() ?: error("No transaction to suspend"),
            ).apply {
                synchronizationManager.unbindResource(database)

                @OptIn(InternalApi::class)
                ThreadLocalTransactionsStack.popTransaction()
            }
        }
    }

    override fun doResume(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any?,
        suspendedResources: Any
    ): Mono<Void?> {
        return mono {
            val suspendedObject = suspendedResources as SuspendedObject

            synchronizationManager.bindResource(database, suspendedObject.transaction)

            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.pushTransaction(suspendedObject.transaction)

            null
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
        return mono {
            val trxObject = transaction as ExposedTransactionObject

            @OptIn(InternalApi::class)
            val currentTransaction = currentTransactionOrNull() as R2dbcTransaction?
            val outerTransactionToUse = if (currentTransaction?.db == database) {
                currentTransaction
            } else {
                null
            }

            @OptIn(InternalApi::class)
            val newTransaction = trxObject.database.transactionManager.newTransaction(
                isolation = definition.isolationLevel.asIsolationLevel(),
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

            if (outerTransactionToUse == null) {
                synchronizationManager.bindResource(database, newTransaction)
            }

            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.pushTransaction(newTransaction)

            null
        }
    }

    override fun doCommit(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void?> {
        return mono {
            val trxObject = status.transaction as ExposedTransactionObject
            trxObject.commit()

            null
        }
    }

    override fun doRollback(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void?> {
        return mono {
            val trxObject = status.transaction as ExposedTransactionObject
            trxObject.rollback()

            null
        }
    }

    override fun doCleanupAfterCompletion(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any
    ): Mono<Void?> {
        return mono {
            val trxObject = transaction as ExposedTransactionObject

            trxObject.cleanUpTransactionIfIsPossible {
                closeStatementsAndConnections(it)
                synchronizationManager.unbindResource(database)
            }

            @OptIn(InternalApi::class)
            ThreadLocalTransactionsStack.popTransaction()

            null
        }
    }

    private suspend fun closeStatementsAndConnections(transaction: R2dbcTransaction) {
        val currentStatement = transaction.currentStatement
        @Suppress("TooGenericExceptionCaught")
        try {
            currentStatement?.let {
                it.closeIfPossible()
                transaction.currentStatement = null
            }
            transaction.closeExecutedStatements()
        } catch (error: Exception) {
            exposedLogger.warn("Statements close failed", error)
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            transaction.close()
        } catch (error: Exception) {
            exposedLogger.warn("Transaction close failed: ${error.message}. Statement: $currentStatement", error)
        }
    }

    override fun doSetRollbackOnly(
        synchronizationManager: TransactionSynchronizationManager,
        status: GenericReactiveTransaction
    ): Mono<Void?> {
        return mono {
            val trxObject = status.transaction as ExposedTransactionObject
            trxObject.setRollbackOnly()

            null
        }
    }

    private data class SuspendedObject(
        val transaction: R2dbcTransaction
    )

    private data class ExposedTransactionObject(
        val database: R2dbcDatabase,
        private val outerTransaction: R2dbcTransaction?
    ) {

        private var isRollback: Boolean = false

        suspend fun cleanUpTransactionIfIsPossible(block: suspend (transaction: R2dbcTransaction) -> Unit) {
            val currentTransaction = getCurrentTransaction()
            if (currentTransaction != null) {
                block(currentTransaction)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        suspend fun commit() {
            try {
                getCurrentTransaction()?.commit()
            } catch (error: Exception) {
                throw TransactionSystemException(error.message.orEmpty(), error)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        suspend fun rollback() {
            try {
                getCurrentTransaction()?.rollback()
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
