package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.transactions.currentTransactionOrNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.SmartTransactionObject
import javax.sql.DataSource

/**
 * Transaction Manager implementation that builds on top of Spring's standard transaction workflow.
 *
 * @param dataSource The data source that produces `Connection` objects.
 * @param databaseConfig The configuration that defines custom properties to be used with connections.
 * If none is specified, the default configuration values will be used.
 * @property showSql Whether transaction queries should be logged. Defaults to `false`.
 * @sample org.jetbrains.exposed.v1.spring.transaction.TestConfig
 */
class SpringTransactionManager(
    dataSource: DataSource,
    databaseConfig: DatabaseConfig = DatabaseConfig {},
    private val showSql: Boolean = false,
) : AbstractPlatformTransactionManager() {

    private val database: Database = Database.connect(
        datasource = dataSource, databaseConfig = databaseConfig
    )

    init {
        isNestedTransactionAllowed = databaseConfig.useNestedTransactions
    }

    /**
     * ExposedConnection implements savepoint by itself
     * `useSavepointForNestedTransaction` is use `SavepointManager` for nested transaction
     *
     * So we don't need to use java savepoint for nested transaction
     */
    override fun useSavepointForNestedTransaction() = false

    @OptIn(InternalApi::class)
    override fun doGetTransaction(): Any {
        // Get the transaction for this specific database from the stack
        val outer = TransactionManager.currentOrNull()

        return ExposedTransactionObject(
            database = database,
            outerTransaction = outer,
        )
    }

    @OptIn(InternalApi::class)
    override fun doSuspend(transaction: Any): Any {
        val trxObject = transaction as ExposedTransactionObject

        return SuspendedObject(
            transaction = trxObject.getCurrentTransaction() ?: error("No transaction to suspend")
        ).apply {
            ThreadLocalTransactionsStack.popTransaction()
        }
    }

    @OptIn(InternalApi::class)
    override fun doResume(transaction: Any?, suspendedResources: Any) {
        val suspendedObject = suspendedResources as SuspendedObject
        ThreadLocalTransactionsStack.pushTransaction(suspendedObject.transaction)
    }

    private data class SuspendedObject(
        val transaction: JdbcTransaction
    )

    override fun isExistingTransaction(transaction: Any): Boolean {
        val trxObject = transaction as ExposedTransactionObject
        return trxObject.getCurrentTransaction() != null
    }

    @OptIn(InternalApi::class)
    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        val trxObject = transaction as ExposedTransactionObject

        // If the current transaction in the stack is null (because it was suspended),
        // or if it belongs to a different database, then we should not use it as outer transaction
        val currentTransaction = currentTransactionOrNull() as JdbcTransaction?
        val outerTransactionToUse = if (currentTransaction?.db == database) {
            currentTransaction
        } else {
            null
        }

        val newTransaction = trxObject.database.transactionManager.newTransaction(
            isolation = definition.isolationLevel,
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

        ThreadLocalTransactionsStack.pushTransaction(newTransaction)
    }

    override fun doCommit(status: DefaultTransactionStatus) {
        val trxObject = status.transaction as ExposedTransactionObject
        trxObject.commit()
    }

    override fun doRollback(status: DefaultTransactionStatus) {
        val trxObject = status.transaction as ExposedTransactionObject
        trxObject.rollback()
    }

    @OptIn(InternalApi::class)
    override fun doCleanupAfterCompletion(transaction: Any) {
        val trxObject = transaction as ExposedTransactionObject

        trxObject.cleanUpTransactionIfIsPossible {
            closeStatementsAndConnections(it)
        }

        ThreadLocalTransactionsStack.popTransaction()
    }

    private fun closeStatementsAndConnections(transaction: JdbcTransaction) {
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

    override fun doSetRollbackOnly(status: DefaultTransactionStatus) {
        val trxObject = status.transaction as ExposedTransactionObject
        trxObject.setRollbackOnly()
    }

    private data class ExposedTransactionObject(
        val database: Database,
        val outerTransaction: JdbcTransaction?,
    ) : SmartTransactionObject {

        private var isRollback: Boolean = false

        fun cleanUpTransactionIfIsPossible(block: (transaction: JdbcTransaction) -> Unit) {
            val currentTransaction = getCurrentTransaction()
            if (currentTransaction != null) {
                block(currentTransaction)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun commit() {
            try {
                getCurrentTransaction()?.commit()
            } catch (error: Exception) {
                throw TransactionSystemException(error.message.orEmpty(), error)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun rollback() {
            try {
                getCurrentTransaction()?.rollback()
            } catch (error: Exception) {
                throw TransactionSystemException(error.message.orEmpty(), error)
            }
        }

        @OptIn(InternalApi::class)
        fun getCurrentTransaction(): JdbcTransaction? {
            // Get the transaction for this specific database from the stack
            return ThreadLocalTransactionsStack.getTransactionOrNull(database) as JdbcTransaction?
        }

        fun setRollbackOnly() {
            isRollback = true
        }

        override fun isRollbackOnly() = isRollback

        override fun flush() {
            // Do nothing
        }
    }
}
